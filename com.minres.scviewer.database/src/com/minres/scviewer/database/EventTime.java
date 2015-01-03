/*******************************************************************************
 * Copyright (c) 2012 IT Just working.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IT Just working - initial API and implementation
 *******************************************************************************/
package com.minres.scviewer.database;

public class EventTime implements Comparable<EventTime>{
	
	public static final double NS = 1000000.0;
	
	public static final double MS = 1000000000.0;

	private long value; // unit is femto seconds
	
	public EventTime(Long value, String unit){
		setValue(value, unit);
	}
	
	public long getValue(){
		return(value);
	}
	
	public double getScaledValue(double scale){
		return value/scale;
	}
	
	public double getValueNS(){
		return getScaledValue(NS);
	}

	public double getValueMS(){
		return getScaledValue(MS);
	}

	public void setValue(long value){
		this.value=value;
	}
	
	public void setValue(long value, String unit){
		this.value=value;
		if("fs".compareToIgnoreCase(unit)==0)
			this.value=value;
		else if("ps".compareToIgnoreCase(unit)==0)
			this.value=value*1000;
		else if("ns".compareToIgnoreCase(unit)==0)
			this.value=value*1000000;
		else if("us".compareToIgnoreCase(unit)==0)
			this.value=value*1000000000;
		else if("ms".compareToIgnoreCase(unit)==0)
			this.value=value*1000000000000L;
		else if("s".compareToIgnoreCase(unit)==0)
			this.value=value*1000000000000000L;
		else {
			System.err.print("Don't know what to do with "+unit+"\n");
		}
	}
	
	public String toString(){
		return value/1000000 +"ns";
	}

	@Override
	public int compareTo(EventTime other) {
		return this.value<other.value? -1 : this.value==other.value? 0 : 1;
	}
}