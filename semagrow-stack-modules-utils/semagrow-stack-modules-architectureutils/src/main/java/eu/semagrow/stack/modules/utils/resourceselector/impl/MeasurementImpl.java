/**
 * 
 */
package eu.semagrow.stack.modules.utils.resourceselector.impl;

import eu.semagrow.stack.modules.utils.resourceselector.Measurement;

/* (non-Javadoc)
 * @see eu.semagrow.stack.modules.utils.resourceselector.Measurement
 */
public class MeasurementImpl implements Measurement {
	
	private long id;
	private String type;
	private int value;
	
	/**
	 * @param id
	 * @param type
	 * @param value
	 */
	public MeasurementImpl(long id, String type, int value) {
		super();
		this.id = id;
		this.type = type;
		this.value = value;
	}
	
	/* (non-Javadoc)
	 * @see eu.semagrow.stack.modules.utils.resourceselector.Measurement#getId()
	 */
	public long getId() {
		return id;
	}
	
	/* (non-Javadoc)
	 * @see eu.semagrow.stack.modules.utils.resourceselector.Measurement#getType()
	 */
	public String getType() {
		return type;
	}
	
	/* (non-Javadoc)
	 * @see eu.semagrow.stack.modules.utils.resourceselector.Measurement#getValue()
	 */
	public int getValue() {
		return value;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	/* (non-Javadoc)
	 * @see eu.semagrow.stack.modules.utils.resourceselector.Measurement#toString()
	 */
	@Override
	public String toString() {
		return "Measurement [id=" + id + ", type=" + type + ", value=" + value
				+ "]";
	}
	
	

}
