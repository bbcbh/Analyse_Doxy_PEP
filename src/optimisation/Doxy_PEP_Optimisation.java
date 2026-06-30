package optimisation;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.apache.commons.math3.analysis.MultivariateFunction;

public class Doxy_PEP_Optimisation extends Abstract_Optimisation {

	public Doxy_PEP_Optimisation(String path_dirName, String path_seed_file) throws IOException {
		super(path_dirName, path_seed_file);		
	}

	@Override
	protected MultivariateFunction generateObjectiveFunc(int seed_row, String[] seed_file_def_val) {
		HashMap<String, Object> setting = new HashMap<>();	
		
		setting.put(ResidualFunc_Doxy_PEP.SETTING_SIM_DIR, new File(simDirPath));
		setting.put(ResidualFunc_Doxy_PEP.SETTING_SEED_DIRNAME, seedDirName);
		setting.put(ResidualFunc_Doxy_PEP.SETTING_SEED_ROW_NUM, seed_row);
		setting.put(ResidualFunc_Doxy_PEP.SETTING_SEED_HEADER, seed_file_header);
		setting.put(ResidualFunc_Doxy_PEP.SETTING_SEED_DEF_VAL, seed_file_def_val);
		
		
		setting.put(ResidualFunc_Doxy_PEP.SETTING_PARAM_CONST, param_const);
		setting.put(ResidualFunc_Doxy_PEP.SETTING_PARAM_OPT, param_to_opt);
		setting.put(ResidualFunc_Doxy_PEP.SETTING_PARAM_ALL_RANGE, param_all_sample_range);		
		setting.put(ResidualFunc_Doxy_PEP.SETTING_CROSS_REF_MAP, cross_ref_map);	
		
		
		ResidualFunc_Doxy_PEP func = new ResidualFunc_Doxy_PEP(setting);		
		
		
		return func;
	}

}
