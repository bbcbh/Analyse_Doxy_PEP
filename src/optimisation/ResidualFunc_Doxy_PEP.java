package optimisation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.commons.math3.analysis.MultivariateFunction;

import relationship.ContactMap;
import sim.Runnable_ClusterModel_Prophylaxis;
import sim.SimulationInterface;
import sim.Simulation_ClusterModelTransmission;
import util.StaticMethods;

public class ResidualFunc_Doxy_PEP implements MultivariateFunction {

	public static final String SETTING_SIM_DIR = "SETTING_SIM_DIR";
	public static final String SETTING_SEED_DIR = "SETTING_SEED_DIR";
	public static final String SETTING_SEED_ROW_NUM = "SETTING_SEED_ROW_NUM";
	public static final String SETTING_SEED_HEADER = "SETTING_SEED_HEADER";
	public static final String SETTING_SEED_DEF_VAL = "SETTING_SEED_DEF_VAL";
	public static final String SETTING_PARAM_OPT = "SETTING_PARAM_OPT";
	public static final String SETTING_CROSS_REF_MAP = "SETTING_CROSS_REF_MAP";

	protected final File path_sim_dir;
	protected final File path_seed_dir;
	protected final int seed_row_num;
	protected final String[] seed_file_header;
	protected final String[] seed_file_def_val;
	protected final HashMap<String, Integer> lookup_seed_list_param_index;

	protected final String[] param_to_opt;
	protected final HashMap<String, String> cross_ref_map;

	private HashMap<String, Double> eval_point_cache;
	private double minResidue = Double.POSITIVE_INFINITY;

	// For simulation
	private ContactMap base_cMap = null;
	private Properties sim_prop;

	@SuppressWarnings("unchecked")
	public ResidualFunc_Doxy_PEP(Map<String, Object> setting) {
		super();
		path_sim_dir = (File) setting.get(SETTING_SIM_DIR);
		path_seed_dir = (File) setting.get(SETTING_SEED_DIR);
		seed_row_num = Integer.valueOf(setting.get(SETTING_SEED_ROW_NUM).toString());
		seed_file_header = (String[]) setting.get(SETTING_SEED_HEADER);
		seed_file_def_val = (String[]) setting.get(SETTING_SEED_DEF_VAL);
		param_to_opt = (String[]) setting.get(SETTING_PARAM_OPT);
		cross_ref_map = ((HashMap<String, String>) setting.get(SETTING_CROSS_REF_MAP));

		// Seed list index
		lookup_seed_list_param_index = new HashMap<>();
		for (int i = 0; i < seed_file_header.length; i++) {
			lookup_seed_list_param_index.put(seed_file_header[i], i);
		}

		// Write previous values to cache
		eval_point_cache = new HashMap<>();

		File file_result_cache = new File(path_sim_dir, String.format(Abstract_Optimisation.fileformat_point_cache,
				String.format("%s_%d", path_seed_dir.getName(), seed_row_num)));
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

		// Load sim prop
		File propFile = new File(path_sim_dir, SimulationInterface.FILENAME_PROP);
		try {
			FileInputStream fIS = new FileInputStream(propFile);
			sim_prop = new Properties();
			sim_prop.loadFromXML(fIS);
			fIS.close();
		} catch (Exception e) {
			e.printStackTrace(System.err);
			System.exit(-1);

		}

	}

	@Override
	public double value(double[] point) {
		// Check if it is already cached
		if (eval_point_cache.containsKey(Arrays.toString(point))) {
			double res = eval_point_cache.get(Arrays.toString(point)).doubleValue();
			return res;
		} else {
			return value_eval(point);
		}
	}

	public double value_eval(double[] point) {
		long cMap_seed = Long.parseLong(seed_file_def_val[0]);
		long sim_seed = Long.parseLong(seed_file_def_val[1]);
		
		if (base_cMap == null) {
			File cMap_dir = new File(sim_prop.getProperty(Simulation_ClusterModelTransmission.PROP_CONTACT_MAP_LOC));
			Pattern pattern_cMap = Pattern.compile(Simulation_ClusterModelTransmission.FILENAME_FORMAT_ALL_CMAP
					.replaceFirst("%d", seed_file_def_val[0]).replaceAll("%d", "(-{0,1}(?!0)\\\\d+)"));

			File[] cMap_files = cMap_dir.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return pattern_cMap.matcher(pathname.getName()).matches();
				}
			});

			StringWriter cMap_str = new StringWriter();
			PrintWriter pWri = new PrintWriter(cMap_str);
			for (File contactMapFile : cMap_files) {
				try {
					BufferedReader reader = new BufferedReader(new FileReader(contactMapFile));
					String line;
					while ((line = reader.readLine()) != null) {
						pWri.println(line);
					}
					reader.close();
				} catch (IOException ex) {
					ex.printStackTrace(System.err);
				}
			}
			pWri.close();

			try {
				base_cMap = ContactMap.ContactMapFromFullString(cMap_str.toString());
			} catch (IOException ex) {
				ex.printStackTrace(System.err);
			}

		}

		// TODO: To be implemented
		Runnable_ClusterModel_Prophylaxis runnable = new Runnable_ClusterModel_Prophylaxis(cMap_seed, sim_seed,
				base_cMap, sim_prop);

		return -1;
	}

}
