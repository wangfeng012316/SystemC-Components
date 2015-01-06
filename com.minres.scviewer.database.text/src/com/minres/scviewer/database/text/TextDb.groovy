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
package com.minres.scviewer.database.text;

import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.minres.scviewer.database.AssociationType;
import com.minres.scviewer.database.DataType;
import com.minres.scviewer.database.HierNode;
import com.minres.scviewer.database.ITxAttributeType;
import com.minres.scviewer.database.ITxAttribute;
import com.minres.scviewer.database.IWaveformDb;
import com.minres.scviewer.database.ITxGenerator;
import com.minres.scviewer.database.IHierNode;
import com.minres.scviewer.database.ITxStream;
import com.minres.scviewer.database.InputFormatException;
import com.minres.scviewer.database.EventTime
import com.minres.scviewer.database.RelationType

public class TextDb extends HierNode implements IWaveformDb{

	private EventTime maxTime;
	
	def streams = []
	
	def relationTypes=[:]
	
	public TextDb() {
		super("TextDb");
	}

	public String getFullName() {
		return getName();
	}

	@Override
	public EventTime getMaxTime() {
		return maxTime;
	}

	public ITxStream getStreamByName(String name){
		streams.find{ITxStream stream-> stream.fullName == name }
	}
	
	public List<ITxStream> getAllWaves() {
		return new LinkedList<ITxStream>(streams);
	}

	public List<IHierNode>  getChildNodes() {
		return childs.sort{it.name};
	}

	public Map<Long, ITxGenerator> getGeneratorsById() {
		TreeMap<Long, ITxGenerator> res = new TreeMap<Long, ITxGenerator>();
		streams.each{TxStream stream -> stream.generators.each{res.put(it.id, id)} }
		return res;
	}

	void clear(){
		streams = []
		maxTime=new EventTime(0, "ns")
	}
	
	void load(File file) throws InputFormatException {
		this.name = file.name;
		parseInput(file)
	}

	private def parseInput(File input){
		def streamsById = [:]
		def generatorsById = [:]
		def transactionsById = [:]
		TxGenerator generator
		Tx transaction
		boolean endTransaction=false
		def matcher
		input.eachLine { line ->
			def tokens = line.split(/\s+/)
			switch(tokens[0]){
				case "scv_tr_stream":
				case "scv_tr_generator":
				case "begin_attribute":
				case "end_attribute":
					if ((matcher = line =~ /^scv_tr_stream\s+\(ID (\d+),\s+name\s+"([^"]+)",\s+kind\s+"([^"]+)"\)$/)) {
						def id = Integer.parseInt(matcher[0][1])
						def stream = new TxStream(id, this, matcher[0][2], matcher[0][3])
						streams<<stream
						streamsById[id]=stream
					} else if ((matcher = line =~ /^scv_tr_generator\s+\(ID\s+(\d+),\s+name\s+"([^"]+)",\s+scv_tr_stream\s+(\d+),$/)) {
						def id = Integer.parseInt(matcher[0][1])
						ITxStream stream=streamsById[Integer.parseInt(matcher[0][3])]
						generator=new TxGenerator(id, stream, matcher[0][2])
						stream.generators<<generator
						generatorsById[id]=generator
					} else if ((matcher = line =~ /^begin_attribute \(ID (\d+), name "([^"]+)", type "([^"]+)"\)$/)) {
						generator.begin_attrs << TxAttributeType.getAttrType(matcher[0][2], DataType.valueOf(matcher[0][3]), AssociationType.BEGIN)
					} else if ((matcher = line =~ /^end_attribute \(ID (\d+), name "([^"]+)", type "([^"]+)"\)$/)) {
						generator.end_attrs << TxAttributeType.getAttrType(matcher[0][2], DataType.valueOf(matcher[0][3]), AssociationType.END)
					}
					break;
				case ")":
					generator=null
					break
				case "tx_begin"://matcher = line =~ /^tx_begin\s+(\d+)\s+(\d+)\s+(\d+)\s+([munpf]?s)/
					def id = Integer.parseInt(tokens[1])
					TxGenerator gen=generatorsById[Integer.parseInt(tokens[2])]
					transaction = new Tx(id, gen.stream, gen, new EventTime(Integer.parseInt(tokens[3]), tokens[4]))
					gen.transactions << transaction
					transactionsById[id]= transaction
					gen.begin_attrs_idx=0;
					maxTime = maxTime>transaction.beginTime?maxTime:transaction.beginTime
					endTransaction=false
					break
				case "tx_end"://matcher = line =~ /^tx_end\s+(\d+)\s+(\d+)\s+(\d+)\s+([munpf]?s)/
					def id = Integer.parseInt(tokens[1])
					transaction = transactionsById[id]
					assert Integer.parseInt(tokens[2])==transaction.generator.id
					transaction.endTime = new EventTime(Integer.parseInt(tokens[3]), tokens[4])
					transaction.generator.end_attrs_idx=0;
					maxTime = maxTime>transaction.endTime?maxTime:transaction.endTime
					endTransaction=true
					break
				case "tx_record_attribute"://matcher = line =~ /^tx_record_attribute\s+(\d+)\s+"([^"]+)"\s+(\S+)\s*=\s*(.+)$/
					def id = Integer.parseInt(tokens[1])
					transactionsById[id].attributes<<new TxAttribute(tokens[2][1..-2], DataType.valueOf(tokens[3]), AssociationType.RECORD, tokens[5..-1].join(' '))
					break
				case "a"://matcher = line =~ /^a\s+(.+)$/
					if(endTransaction){
						transaction.attributes << new TxAttribute(transaction.generator.end_attrs[0], tokens[1])
					} else {
						transaction.attributes << new TxAttribute(transaction.generator.begin_attrs[0], tokens[1])
					}
					break
				case "tx_relation"://matcher = line =~ /^tx_relation\s+\"(\S+)\"\s+(\d+)\s+(\d+)$/
					Tx tr1= transactionsById[Integer.parseInt(tokens[2])]
					Tx tr2= transactionsById[Integer.parseInt(tokens[3])]
					def relType=tokens[1][1..-2]
					if(!relationTypes.containsKey(relType)) relationTypes[relType]=new RelationType(relType)
					def rel = new TxRelation(relationTypes[relType], tr1, tr2)
					tr1.outgoingRelations<<rel
					tr2.incomingRelations<<rel
					break
				default:
					println "Don't know what to do with: '$line'"

			}
		}
		addHierarchyNodes()
	}

	def addHierarchyNodes(){
		streams.each{ TxStream stream->
			def hier = stream.fullName.split(/\./)
			IHierNode node = this
			hier.each { name ->
				def n1 = node.childNodes.find{it.name == name}
				if(name == hier[-1]){ //leaf
					if(n1!=null) {
						if(n1 instanceof HierNode){
							node.childNodes.remove(n1)
							stream.childNodes.addAll(n1.childNodes)
						} else {
							throw new InputFormatException()
						}
					}
					stream.name=name
					node.childNodes<<stream
					node=stream
				} else { // intermediate
					if(n1 != null) {
						node=n1
					} else {
						HierNode newNode = new HierNode(name)
						node.childNodes<<newNode
						node=newNode
					}
				}
			}
		}
	}

}
