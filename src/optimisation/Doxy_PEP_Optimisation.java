package optimisation;

import java.io.IOException;

import org.apache.commons.math3.analysis.MultivariateFunction;

public class Doxy_PEP_Optimisation extends Abstract_Optimisation {

	public Doxy_PEP_Optimisation(String dirName, String seed_name) throws IOException {
		super(dirName, seed_name);		
	}

	@Override
	protected MultivariateFunction generateObjectiveFunc(int seed_row, String[] seed_file_def_val) {


		return null;
	}

}
