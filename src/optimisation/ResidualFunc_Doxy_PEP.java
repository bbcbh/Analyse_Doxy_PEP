package optimisation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.math3.analysis.MultivariateFunction;

import sim.Abstract_Runnable_ClusterModel_Transmission;
import sim.Runnable_ClusterModel_MultiTransmission;
import sim.Runnable_ClusterModel_Prophylaxis;
import sim.Simulation_ClusterModelTransmission;
import sim.Simulation_DoxyPEP;
import util.StaticMethods;

public class ResidualFunc_Doxy_PEP implements MultivariateFunction {

	public static final String SETTING_SIM_DIR = "SETTING_SIM_DIR";
	public static final String SETTING_SEED_DIRNAME = "SETTING_SEED_DIR";
	public static final String SETTING_SEED_ROW_NUM = "SETTING_SEED_ROW_NUM";
	public static final String SETTING_SEED_HEADER = "SETTING_SEED_HEADER";
	public static final String SETTING_SEED_DEF_VAL = "SETTING_SEED_DEF_VAL";

	public static final String SETTING_CROSS_REF_MAP = "SETTING_CROSS_REF_MAP";
	// public static final String SETTING_PARAM_OPT_BOUND = "SETTING_PARAM_BOUND";

	public static final String SETTING_PARAM_CONST = "SETTING_PARAM_CONST";
	public static final String SETTING_PARAM_OPT = "SETTING_PARAM_OPT";
	public static final String SETTING_PARAM_ALL_RANGE = "SETTING_PARAM_ALL_RANGE";

	protected final File path_sim_dir;
	protected final File path_seed_dirName;
	protected final int seed_row_num;
	protected final String[] seed_file_header;
	protected final String[] seed_file_def_val;

	protected final HashMap<String, Integer> lookup_seed_list_param_index;
	protected final HashMap<String, Integer> lookup_opt_param_index;
	protected final HashMap<String, double[]> lookup_opt_param_bound;

	protected final HashMap<String, String> cross_ref_map;

	private HashMap<String, Double> eval_point_cache;
	private double minResidue = Double.POSITIVE_INFINITY;

	// For simulation	
//	private Properties sim_prop;

	@SuppressWarnings("unchecked")
	public ResidualFunc_Doxy_PEP(Map<String, Object> setting) {
		super();
		path_sim_dir = (File) setting.get(SETTING_SIM_DIR);
		path_seed_dirName = new File(path_sim_dir, (String) setting.get(SETTING_SEED_DIRNAME));
		seed_row_num = Integer.valueOf(setting.get(SETTING_SEED_ROW_NUM).toString());
		seed_file_header = (String[]) setting.get(SETTING_SEED_HEADER);
		seed_file_def_val = (String[]) setting.get(SETTING_SEED_DEF_VAL);
		cross_ref_map = ((HashMap<String, String>) setting.get(SETTING_CROSS_REF_MAP));

		// Seed list param index
		lookup_seed_list_param_index = new HashMap<>();
		for (int i = 2; i < seed_file_header.length; i++) {
			lookup_seed_list_param_index.put(seed_file_header[i], i - 2); // Offset with CMAP_SEED and SIM_SEED
		}

		// Opt parameter index
		String[] param_to_opt = (String[]) setting.get(SETTING_PARAM_OPT);
		lookup_opt_param_index = new HashMap<>();
		for (int i = 0; i < param_to_opt.length; i++) {
			lookup_opt_param_index.put(param_to_opt[i], i);
		}

		// Parameter boundaries
		lookup_opt_param_bound = (HashMap<String, double[]>) setting.get(SETTING_PARAM_ALL_RANGE);

		// Write previous values to cache
		eval_point_cache = new HashMap<>();

		File file_result_cache = new File(path_sim_dir, String.format(Abstract_Optimisation.fileformat_point_cache,
				String.format("%s_%d", path_seed_dirName.getName(), seed_row_num)));
		if (file_result_cache.exists()) {
			try {
				String[] ent = StaticMethods.extracted_lines_from_text(file_result_cache);
				for (String line : ent) {
					String[] pair = line.split(":");
					eval_point_cache.put(pair[0], Double.valueOf(pair[1]));
				}
			} catch (IOException e) {
				e.printStackTrace(System.err);
			}

		}

//		// Load sim prop
//		File propFile = new File(path_sim_dir, SimulationInterface.FILENAME_PROP);
//		try {
//			FileInputStream fIS = new FileInputStream(propFile);
//			sim_prop = new Properties();
//			sim_prop.loadFromXML(fIS);
//			fIS.close();
//		} catch (Exception e) {
//			e.printStackTrace(System.err);
//			System.exit(-1);
//		}

		// Correct input seed
		boolean hasCorrection = false;
		String[] org_def = Arrays.copyOf(seed_file_def_val, seed_file_def_val.length);

		for (String adjParam : cross_ref_map.keySet()) {
			if (lookup_opt_param_index.containsKey(adjParam)) {
				int adjParam_index = lookup_seed_list_param_index.get(adjParam);
				int baseParam_index = lookup_seed_list_param_index.get(cross_ref_map.get(adjParam));

				double baseParam_val = Double.parseDouble(seed_file_def_val[baseParam_index]);
				double crossRef_val = Double.parseDouble(seed_file_def_val[adjParam_index]);

				// crossRef_bound offset by two due to seed
				double[] crossRef_bound = lookup_opt_param_bound.get(adjParam);

				if (crossRef_bound != null) {
					if (!(crossRef_bound[0] <= crossRef_val && crossRef_val <= crossRef_bound[1])) {
						// Adjust to mid point
						crossRef_val = baseParam_val * (crossRef_bound[0] + crossRef_bound[1]) / 2;

						String adjVal_str = Double.toString(crossRef_val);
						hasCorrection |= !seed_file_def_val[adjParam_index].equals(adjVal_str);
						seed_file_def_val[adjParam_index] = adjVal_str;
					}
				}
			}
		}

		if (hasCorrection) {
			System.out.printf("Pre-opt: Following parameter adjusted due to boundary setting:\n");
			for (int i = 0; i < seed_file_header.length; i++) {
				if (!org_def[i].equals(seed_file_def_val[i])) {
					System.out.printf("%s: %s -> %s\n", seed_file_header[i], org_def[i], seed_file_def_val[i]);
				}
			}

			// Replace old seed file
			try {
				File seedFile = new File(path_seed_dirName, String.format("%s.csv", path_seed_dirName.getName()));
				Files.move(seedFile.toPath(),
						new File(path_seed_dirName, String.format("org_%s", seedFile.getName())).toPath(),
						StandardCopyOption.REPLACE_EXISTING);

				PrintWriter pri = new PrintWriter(seedFile);
				for (int i = 0; i < seed_file_header.length; i++) {
					if (i > 0) {
						pri.print(',');
					}
					pri.print(seed_file_header[i]);
				}
				pri.println();
				for (int i = 0; i < seed_file_def_val.length; i++) {
					if (i > 0) {
						pri.print(',');
					}
					pri.print(seed_file_def_val[i]);
				}
				pri.println();

				pri.close();
			} catch (IOException ex) {
				ex.printStackTrace(System.err);
			}

		}

	}

	@Override
	public double value(double[] opt_point) {
		double[] func_point = new double[seed_file_def_val.length - 2];
		for (int i = 0; i < func_point.length; i++) {
			func_point[i] = Double.parseDouble(seed_file_def_val[i + 2]);
		}

		// Adjust value based on parameter
		for (Entry<String, Integer> opt_ent : lookup_opt_param_index.entrySet()) {
			double opt_val = opt_point[opt_ent.getValue()];
			int seed_list_index = lookup_seed_list_param_index.get(opt_ent.getKey());
			func_point[seed_list_index] = opt_val;
		}

		// Check for cross ref
		for (String adjParam : cross_ref_map.keySet()) {
			int adjParam_index = lookup_seed_list_param_index.get(adjParam);
			int baseParam_index = lookup_seed_list_param_index.get(cross_ref_map.get(adjParam));
			if (lookup_opt_param_index.containsKey(adjParam)) {
				func_point[adjParam_index] *= func_point[baseParam_index];
			} else {
				double[] crossRef_bound = lookup_opt_param_bound.get(adjParam);
				func_point[adjParam_index] = func_point[baseParam_index] * (crossRef_bound[0] + crossRef_bound[1]) / 2;
			}

		}

		// Check if it is already cached
		if (eval_point_cache.containsKey(Arrays.toString(func_point))) {
			double res = eval_point_cache.get(Arrays.toString(func_point)).doubleValue();
			return res;
		} else {
			HashMap<String, Double> overload_param = new HashMap<>();
			for (int i = 0; i < func_point.length; i++) {
				overload_param.put(seed_file_header[i + 2], func_point[i]);
			}

			return value_eval(overload_param);
		}
	}

	public double value_eval(HashMap<String, Double> overload_param) {		
		double residue_val = Double.POSITIVE_INFINITY;

		int cMapPreLoad = Integer.MAX_VALUE;
		
		final File seedFile = new File(path_seed_dirName, String.format("%s.csv", path_seed_dirName.getName()));

		try {
			Simulation_DoxyPEP sim = new Simulation_DoxyPEP(seedFile) {
				@Override
				public Abstract_Runnable_ClusterModel_Transmission generateDefaultRunnable(long cMap_seed,
						long sim_seed, Properties loadProperties) {
					
					// Set output path to be same as seed file
					loadProperties.put(Runnable_ClusterModel_MultiTransmission.PROP_SEED_FILE_PATH, 							
							seedFile.getAbsolutePath());
					
					Runnable_ClusterModel_Prophylaxis runnable = new Runnable_ClusterModel_Prophylaxis(cMap_seed,
							sim_seed, this.baseContactMapMapping.get(cMap_seed), loadProperties) {
						@Override
						public ArrayList<Integer> loadOptParameter(String[] parameter_settings, double[] point,
								int[][] seedInfectNum, boolean display_only) {

							// Check for overloaded parameters
							for (int i = 0; i < parameter_settings.length; i++) {
								if (overload_param.containsKey(parameter_settings[i])) {
									point[i] = overload_param.get(parameter_settings[i]);
								}
							}
							return super.loadOptParameter(parameter_settings, point, seedInfectNum, display_only);
						}
						
						
					};
					return runnable;
				}
				
				@Override
				protected void finalise_simulations() throws IOException, FileNotFoundException {
					// Skip zipping of file
				}

			};

			String[] arg_sim = new String[] { path_sim_dir.getAbsolutePath(),
					String.format("%1$s%2$s%3$s%2$s.csv", Simulation_ClusterModelTransmission.LAUNCH_ARGS_SEED_MAP,
							path_seed_dirName.getName(), File.separator),
					String.format("%s%d", Simulation_ClusterModelTransmission.LAUNCH_ARGS_PARTIALLOAD_MAP, cMapPreLoad),
					"-export_skip_backup" };

			Simulation_ClusterModelTransmission.launch(arg_sim, sim);

			// TODO: Analyse generated results
			
			System.out.println("Simulation completed.");
			System.exit(-1);

		} catch (Exception e) {
			System.err.printf("Warning! %s encountered during running of parameter set:\n%s\n", e.toString(),
					overload_param.toString());
			e.printStackTrace(System.err);
			System.exit(-1);
		}

		return residue_val;
	}

}
