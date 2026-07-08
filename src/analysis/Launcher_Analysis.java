package analysis;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Launcher_Analysis {

	public static void main(String[] args) throws IOException {

		String usageInfo = "Usage: java -jar Analyse_Doxy_PEP.jar BASEDIR_SIM";
		String[] check_7z = new String[] { "Results_Incidence_Person.7z", "Results_Incidence_Site.7z",
				"Results_Infectious_Prevalence_Person.7z", "Results_Infectious_Prevalence_Site.7z",
				"Results_Treatment_Person.7z" };

		boolean correctUsage = args.length > 0;

		if (correctUsage) {
			File baseDir = new File(args[0]);
			Pattern pattern_seed_dir = Pattern.compile("Seed_List_(\\d+)");

			File[] seedDirs = baseDir.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return pathname.isDirectory() && pattern_seed_dir.matcher(pathname.getName()).matches();
				}
			});

			System.out.printf("Launching analysis on %d simulation results located at %s.\n", seedDirs.length,
					baseDir.getAbsolutePath());

			ArrayList<File> incompleteDir = new ArrayList<>();

			for (File seedDir : seedDirs) {
				boolean simOkay = true;
				for (String s : check_7z) {
					simOkay &= (new File(seedDir, s)).exists();
				}
				if (!simOkay) {
					incompleteDir.add(seedDir);
				}
			}

			if (incompleteDir.size() > 0) {
				System.out.printf("The following seed dir are incomplete (%d in total):\n", incompleteDir.size());
				for (File seedDir : incompleteDir) {
					System.out.printf("   -seedMap=%1$s/%1$s.csv\n", seedDir.getName());
				}

			} else {
				System.out.printf("Generating summary data from %d simulation directories\n", seedDirs.length);

				Pattern pattern_sim_id = Pattern.compile("\\[(.*)\\].*");
				Pattern pattern_sim_col_name = Pattern.compile(".*(\\d+).csv:(\\d+)");

				// K = sim_id, V = col entry
				HashMap<String, String[]> mapEnt_by_sim;
				// K = col_name, V = mapEnt_by_sim
				HashMap<String, HashMap<String, String[]>> mapEnt_by_col;
				// K = res set name, V = mapEnt_by_col
				HashMap<String, HashMap<String, HashMap<String, String[]>>> mapEnt_by_result_set = new HashMap<>();

				Arrays.sort(seedDirs, new Comparator<File>() {
					@Override
					public int compare(File o1, File o2) {
						Matcher m1 = pattern_seed_dir.matcher(o1.getName());
						Matcher m2 = pattern_seed_dir.matcher(o2.getName());
						if (m1.matches() && m2.matches()) {
							return Integer.valueOf(m1.group(1)).compareTo(Integer.valueOf(m2.group(1)));
						} else {
							return 0;
						}
					}
				});

				int maxRow = 0;

				for (File seedDir : seedDirs) {

					Pattern pattern_result_files = Pattern.compile("Results_(.*).7z");
					File[] result_files = seedDir.listFiles(new FileFilter() {
						@Override
						public boolean accept(File pathname) {
							return pattern_result_files.matcher(pathname.getName()).matches();
						}
					});

					String[] result_names = new String[result_files.length];
					for (int i = 0; i < result_names.length; i++) {
						Matcher m = pattern_result_files.matcher(result_files[i].getName());
						m.matches();
						result_names[i] = m.group(1);
					}

					for (String result_name : result_names) {
						File zipFile = new File(seedDir, String.format("Results_%s.7z", result_name));
						if (zipFile.exists()) {
							HashMap<String, ArrayList<String[]>> zip_ent = new HashMap<>();
							zip_ent = util.StaticMethods.extractedLinesFrom7Zip(zipFile, zip_ent, null);

							mapEnt_by_col = mapEnt_by_result_set.get(result_name);
							if (mapEnt_by_col == null) {
								mapEnt_by_col = new HashMap<>();
								mapEnt_by_result_set.put(result_name, mapEnt_by_col);
							}

							String[] fileEnt_names = zip_ent.keySet().toArray(new String[0]);
							Arrays.sort(fileEnt_names);

							for (String fileEnt_name : fileEnt_names) {
								Matcher m = pattern_sim_id.matcher(fileEnt_name);
								m.matches();
								String sim_id = m.group(1).replace(',', ':');
								ArrayList<String[]> fileEnt_val_arr = zip_ent.get(fileEnt_name);
								String[] header_row = fileEnt_val_arr.get(0);

								for (int c = 0; c < header_row.length; c++) {
									String col_header = header_row[c];
									mapEnt_by_sim = mapEnt_by_col.get(col_header);
									if (mapEnt_by_sim == null) {
										mapEnt_by_sim = new HashMap<>();
										mapEnt_by_col.put(col_header, mapEnt_by_sim);
									}
									String[] col_val = new String[fileEnt_val_arr.size()];
									col_val[0] = sim_id;
									for (int r = 1; r < fileEnt_val_arr.size(); r++) {
										col_val[r] = fileEnt_val_arr.get(r)[c];
									}
									mapEnt_by_sim.put(sim_id, col_val);
									maxRow = Math.max(maxRow, col_val.length);
								}

							}

						}

					}

				}

				// Summarise stored results
				for (Entry<String, HashMap<String, HashMap<String, String[]>>> resultSetEnt : mapEnt_by_result_set
						.entrySet()) {
					String resultSetName = resultSetEnt.getKey();
					File summaryDir = new File(baseDir, String.format("Summary_%s", resultSetName));
					summaryDir.mkdirs();
					mapEnt_by_col = resultSetEnt.getValue();

					HashMap<String, String[]> mapEnt_by_sim_time = mapEnt_by_col.remove("Time");

					String[] common_time_col = new String[0];
					for (String[] col : mapEnt_by_sim_time.values()) {
						if (common_time_col.length < col.length) {
							common_time_col = col;
						}
					}

					String[] col_names = mapEnt_by_col.keySet().toArray(new String[0]);

					for (String col_name : col_names) {
						StringBuilder[] lines = new StringBuilder[common_time_col.length];
						for (int i = 0; i < lines.length; i++) {
							lines[i] = new StringBuilder();
							lines[i].append(common_time_col[i]);
						}

						mapEnt_by_sim = mapEnt_by_col.get(col_name);

						String[] sim_names = mapEnt_by_sim.keySet().toArray(new String[0]);
						Arrays.sort(sim_names, new Comparator<String>() {
							@Override
							public int compare(String o1, String o2) {
								Matcher m1 = pattern_sim_col_name.matcher(o1);
								Matcher m2 = pattern_sim_col_name.matcher(o2);
								if (m1.matches() && m2.matches()) {
									int res = 0;
									for (int g = 1; g <= m1.groupCount() && res == 0; g++) {
										res = Integer.valueOf(m1.group(g)).compareTo(Integer.valueOf(m2.group(g)));
									}
									return res;
								} else {
									return 0;
								}
							}
						});

						for (String sim_name : sim_names) {
							lines[0].append(',');
							lines[0].append(sim_name);

							String[] csv_col = mapEnt_by_sim.get(sim_name);
							for (int r = 1; r < lines.length; r++) {
								lines[r].append(',');
								if (r < csv_col.length) {
									lines[r].append(csv_col[r]);
								} else {
									lines[r].append(Double.NaN);
								}
							}
						}

						// Print results
						File summaryFile = new File(summaryDir,
								String.format("Summary_%s_%s.csv", summaryDir.getName(), col_name));
						PrintWriter pWri = new PrintWriter(summaryFile);

						for (int i = 0; i < lines.length; i++) {
							pWri.println(lines[i]);
						}

						pWri.close();

						System.out.printf("%s generated.\n", summaryFile.getAbsolutePath());

					}

				}

			}
		} else {
			System.out.println(usageInfo);
			System.exit(-1);
		}

	}

}
