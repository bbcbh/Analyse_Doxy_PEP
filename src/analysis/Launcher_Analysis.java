package analysis;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class Launcher_Analysis {

	public static void main(String[] args) {

		String usageInfo = "Usage: java -jar Analyse_Doxy_PEP.jar BASEDIR_SIM";
		String[] check_7z = new String[] { "Results_Incidence_Person.7z", "Results_Incidence_Site.7z",
				"Results_Infectious_Prevalence_Person.7z", "Results_Infectious_Prevalence_Site.7z",
				"Results_Treatment_Person.7z" };

		boolean correctUsage = false;

		if (args.length > 0) {
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
				// TODO: next level of analysis

			}
		}

		if (!correctUsage) {
			System.out.println(usageInfo);
			System.exit(0);
		}

	}

}
