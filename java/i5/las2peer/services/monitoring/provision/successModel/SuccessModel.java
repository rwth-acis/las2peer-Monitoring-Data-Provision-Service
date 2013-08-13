package i5.las2peer.services.monitoring.provision.successModel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * 
 * A SuccessModel bundles {@link Measure}s into factors and dimensions.
 * This implementation uses the model of Delone & McLean.
 * 
 * @author Peter de Lange
 *
 */
public class SuccessModel {
	
	
	/**
	 * 
	 * Enumeration with the possible success dimensions.
	 * 
	 * @author Peter de Lange
	 *
	 */
	public enum Dimension {
		SystemQuality,
		InformationQuality,
		Use,
		UserSatisfaction,
		IndividualImpact,
		OrganizationalImpact
	}
	
	private String name;
	//Can be null in case of a node success model
	private String serviceName;
	private List<Factor> factors = new ArrayList<Factor>();
	
	
	/**
	 * 
	 * Constructor of a SuccessModel.
	 * The service name can be set to null of this model should be used for node monitoring.
	 * 
	 * @param name the name of this success model
	 * @param serviceName the service this model is made for
	 * @param factors a list of {@link Factor}s
	 * 
	 */
	public SuccessModel(String name, String serviceName, List<Factor> factors){
		this.name = name;
		this.serviceName = serviceName;
		this.factors = factors;
	}
	
	
	/**
	 * 
	 * Gets the name of this model.
	 * 
	 * @return the name
	 * 
	 */
	public String getName(){
		return this.name;
	}
	
	
	/**
	 * 
	 * Gets the service name of this model or null of none is defined (node model).
	 * 
	 * @return the service name or null
	 * 
	 */
	public String getServiceName(){
		return serviceName;
	}
	
	
	/**
	 * 
	 * Returns all factors of the given success dimension.
	 * 
	 * @param dimension a {@link Dimension}
	 * 
	 * @return a list of {@Factor}s
	 * 
	 */
	public List<Factor> getFactorsOfDimension(Dimension dimension){
		List<Factor> factorsOfDimension = new ArrayList<Factor>();
		Iterator<Factor> iterator = factors.iterator();
		while(iterator.hasNext()){
			Factor factor = iterator.next();
			if(factor.getDimension() == dimension){
				 factorsOfDimension.add(factor);
			}
		}
		return factorsOfDimension;
	}
	
	
}
