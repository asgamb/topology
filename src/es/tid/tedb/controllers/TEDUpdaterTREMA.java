package es.tid.tedb.controllers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.locks.Lock;
import java.util.logging.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.metaparadigm.jsonrpc.JSONSerializer;

import es.tid.tedb.DomainTEDB;
import es.tid.tedb.IntraDomainEdge;
import es.tid.tedb.SimpleTEDB;
import es.tid.tedb.TE_Information;
import es.tid.tedb.controllers.TEDUpdaterController.MyEdge;
import es.tid.tedb.elements.RouterInfoPM;

public class TEDUpdaterTREMA extends TEDUpdaterController
{
	public static String controllerName = "TREMA";
	private Hashtable<Integer,MyEdge> interDomainLinks = new Hashtable<Integer,MyEdge>();
	
	
	//Overwritten father variables and fixing the topology urls in code
	private String topologyPathNodes = "/get_topology/";
	private String topologyPathLinks = "/get_graph/";//??
	
	public TEDUpdaterTREMA(String ip, String port, String topologyPathLinks, String topologyPathNodes,DomainTEDB ted, Logger log)
	{
		super( ip,  port,  topologyPathLinks,  topologyPathNodes, ted,  log);
	}
	
	public TEDUpdaterTREMA(String ip, String port, String topologyPathLinks, String topologyPathNodes,DomainTEDB ted, Logger log, Lock lock)
	{
		super( ip,  port,  topologyPathLinks,  topologyPathNodes, ted,  log,  lock);
	}
	
	public TEDUpdaterTREMA(ArrayList<String> ips, ArrayList<String>ports , String topologyPathLinks, String topologyPathNodes,DomainTEDB ted, Logger log)
	{
		super(ips, ports ,  topologyPathLinks,  topologyPathNodes, ted,  log);
	}
	
	
	@Override
	public void run()
	{	
		
		if(interDomainFile != null)
		{
			interDomainLinks = TEDUpdaterController.readInterDomainFile(interDomainFile);
		}
		
		String responseLinks = "";
		String responseNodes = "";
		
		try
		{
			
			Hashtable<String,RouterInfoPM> nodes = new Hashtable<String,RouterInfoPM>();
			
			for (int i = 0; i < ips.size(); i++)
			{
				responseNodes = queryForNodes(ips.get(i), ports.get(i));//query for topology
				parseNodes(responseNodes, nodes, ips.get(i), ports.get(i));
				log.info("responseNodes:::"+responseNodes);
			}
			
		}
		catch (Exception e)
		{
			log.info(e.toString());
		}
	}
	
	
	private String KDDItoFloodlight(String BristolFormat)
	{
		String floodFormat = new String(BristolFormat);
		//Por algo se me conoce como el hacker
		for (int i = 2; i < floodFormat.length(); i += 3)
		{
			floodFormat =  floodFormat.substring(0, i) + ":" + floodFormat.substring(i, floodFormat.length());
		}
		log.info("BristolFormat--> " + BristolFormat + ", floodFormat-->" + floodFormat);
		return floodFormat;
	}

	private void parseNodes(String response, Hashtable<String,RouterInfoPM> routerInfoList, String ip, String port)
	{	
		try
		{
			JSONParser parser = new JSONParser();
			Object obj = parser.parse(response);
		
			JSONObject jsonaux=(JSONObject) obj;
			String host=(String)jsonaux.get("hostConfig").toString();
			obj=parser.parse(host);
			JSONObject jsonhost=(JSONObject) obj;
			
			//FIXME: This info has been taken from TREMA controller but is not used. Take a look if it's necessary
			String OPS_Label=(String)jsonhost.get("OPS label");
			String Port_id=(String)jsonhost.get("Port_id");
			String reach_nodes=(String)jsonhost.get("Reachable nodes");
			String node_type=(String)jsonhost.get("nodeType");//Used for RouterType
			
			RouterInfoPM rInfo = new RouterInfoPM();
			String dpid=((String)jsonhost.get("Virtual_node_id")).replace("-", ":");
			rInfo.setRouterID(dpid);//Bristol to floodlight?
			rInfo.setConfigurationMode("Openflow");
			rInfo.setControllerType(TEDUpdaterNOX.controllerName);
			rInfo.setRouterType(node_type);
			
			rInfo.setReachable_nodes(parseReachability(reach_nodes));
			
			rInfo.setControllerIdentifier(ip, port);
			rInfo.setControllerIP(ip);
			rInfo.setControllerPort(port);
			
			routerInfoList.put(rInfo.getRouterID(),rInfo);
			((SimpleTEDB)TEDB).getNetworkGraph().addVertex(rInfo);
			//Getting Links
			//parseLinks(???);
			//parseLinks(dpid, jsonarray)
		}
		catch (Exception e)
		{
			log.info(e.toString());
		}
	}
	
	private LinkedList<String> parseReachability(String reach_nodes) {
		boolean end=false;
		int beginIndex=0;
		LinkedList<String> nodes= new LinkedList<String>();
		reach_nodes=reach_nodes.replace(" ", "");
		System.out.println("Reachable Nodes:"+reach_nodes);
		while (!end){
			int pointer=reach_nodes.indexOf(",");
			if (pointer==-1)
				end=true;
			else {
				String node=reach_nodes.substring(beginIndex, pointer);
				System.out.println("Reachable Node:"+node);
				reach_nodes=reach_nodes.substring(pointer+1);
				nodes.add(node);
			}
		}
		if (reach_nodes.length()>0){
			System.out.println("Reachable Node:"+reach_nodes);
			nodes.add(reach_nodes);
		}
		return nodes;
	}

	private void parseLinks(String dpid,String links, Hashtable<String,RouterInfoPM> nodes) //NOX Parse Links. it's not used in TREMA
	{
		try {
			//log.info("Inside parseJSON");
			JSONParser parser = new JSONParser();
			Object obj = parser.parse(links);
		
			JSONArray msg = (JSONArray) obj;
			Iterator<JSONObject> iterator = msg.iterator();
			while (iterator.hasNext()) 
			{
				JSONObject jsonObject = (JSONObject) iterator.next();
				//System.out.println(jsonObject.get("src-switch"));
				IntraDomainEdge edge= new IntraDomainEdge();
				
				String labels = (String) jsonObject.get("labels");//??
				String destnode = ((String) jsonObject.get("peer_node")).replace("-", ":");
				String type = (String) jsonObject.get("port_type");

				RouterInfoPM source = nodes.get(dpid);
				RouterInfoPM dest = nodes.get(destnode);
					
				//((SimpleTEDB)TEDB).getNetworkGraph().addVertex(source);
				//((SimpleTEDB)TEDB).getNetworkGraph().addVertex(dest);
				
				System.out.println("Srcnode: "+dpid);
				System.out.println("Dstnode: "+destnode);
				
				System.out.println("Srcport: "+jsonObject.get("port_id"));
				System.out.println("Dstport: "+jsonObject.get("peer_port"));
				
				if ((dest!= null)&&(source!=null)){
					
				
					edge.setSrc_if_id(Long.parseLong((String)jsonObject.get("port_id")));
					edge.setDst_if_id(Long.parseLong((String)jsonObject.get("peer_port")));
					
					edge.setType(type);
					
					// This is a big problem because info is not initialized from file
					// and the controller doesn't give information about how many wlans
					// the are
					
					TE_Information tE_info = new TE_Information();
					System.out.println("Labels: Original ("+labels+") and Parsed ("+labels.substring(labels.indexOf(",")+1,labels.indexOf("]"))+")");
					tE_info.setNumberWLANs(Integer.parseInt(labels.substring(labels.indexOf(",")+1,labels.indexOf("]"))));
					tE_info.initWLANs();
					
					if (interDomainFile != null)
					{
						completeTE_Information(tE_info, source.getRouterID(), dest.getRouterID());
					}
					
					edge.setTE_info(tE_info);
					
					
					edge.setDirectional(true);
					//Bidirectional case? See other TEDUpdaters.
				
					((SimpleTEDB)TEDB).getNetworkGraph().addEdge(source, dest, edge);
				
				} else {
					System.out.println("Link with an unknown node. Ingnoring...");
				}
				//log.info("Edge added:"+edge);
				//log.info(((SimpleTEDB)TEDB).getIntraDomainLinks().toString());
			}
			//parseRemainingLinksFromXML(nodes);
	 
	 
		} 
		catch (Exception e)
		{
			log.info(e.toString());
		}
	}

	
//	private void parseLinks(String links,Hashtable<String,RouterInfoPM> nodes)
//	{
//		try {
//			//log.info("Inside parseJSON");
//			JSONParser parser = new JSONParser();
//			Object obj = parser.parse(links);
//		
//			JSONArray msg = (JSONArray) obj;
//			Iterator<JSONObject> iterator = msg.iterator();
//			while (iterator.hasNext()) 
//			{
//				JSONObject jsonObject = (JSONObject) iterator.next();
//				//System.out.println(jsonObject.get("src-switch"));
//				IntraDomainEdge edge= new IntraDomainEdge();
//				
//				JSONObject jsonObject_src = (JSONObject) jsonObject.get("src");
//				JSONObject jsonObject_dst = (JSONObject) jsonObject.get("dst");
//
//				RouterInfoPM source = nodes.get(BristoltoFloodlight((String)jsonObject_src.get("dpid")));
//				RouterInfoPM dest = nodes.get(BristoltoFloodlight((String)jsonObject_dst.get("dpid")));
//					
//				//((SimpleTEDB)TEDB).getNetworkGraph().addVertex(source);
//				//((SimpleTEDB)TEDB).getNetworkGraph().addVertex(dest);
//
//				log.info("Adding Vertex->"+source+" hashcode:"+source.hashCode());
//				log.info("Adding Vertex->"+dest+" hashcode:"+dest.hashCode());
//				
//				edge.setSrc_if_id((Long)jsonObject_src.get("port_no"));
//				edge.setDst_if_id((Long)jsonObject_dst.get("port_no"));
//				
//				
//				// This is a big problem because info is not initialized from file
//				// and the controller doesn't give information about how many wlans
//				// the are
//				
//				TE_Information tE_info = new TE_Information();
//				tE_info.setNumberWLANs(15);
//				tE_info.initWLANs();
//				
//				if (interDomainFile != null)
//				{
//					completeTE_Information(tE_info, source.getRouterID(), dest.getRouterID());
//				}
//				
//				edge.setTE_info(tE_info);
//				
//				String isBidirectional = (String)jsonObject.get("direction");
//				
//				
//				// En Bristol los enlaces son unidirecctionales, pero asumimos que hay uno en una direccion
//				// esta el otro
//				isBidirectional = "bidirectional";
//				
//				//log.info("isBidirectional::"+isBidirectional);
//				
//				if ((1==1)||(isBidirectional != null) && (isBidirectional.equals("bidirectional")))
//				{
//					//((SimpleTEDB)TEDB).getNetworkGraph().addEdge(source, dest, edge);
//					
//					TE_Information tE_infoOtherWay = new TE_Information();
//					tE_infoOtherWay.setNumberWLANs(15);
//					tE_infoOtherWay.initWLANs();
//					IntraDomainEdge edgeOtherWay= new IntraDomainEdge();
//					
//					edgeOtherWay.setSrc_if_id((Long)jsonObject_src.get("port_no"));
//					edgeOtherWay.setDst_if_id((Long)jsonObject_dst.get("port_no"));
//					edgeOtherWay.setTE_info(tE_infoOtherWay);
//					
//					((SimpleTEDB)TEDB).getNetworkGraph().addEdge(source, dest, edge);
//					((SimpleTEDB)TEDB).getNetworkGraph().addEdge(dest, source, edgeOtherWay);
//					
//					completeTE_Information(tE_info, dest.getRouterID(), source.getRouterID());
//					
//					log.info("source::"+source);
//					log.info("dest::"+dest);
//					log.info("edgeOtherWay::"+edgeOtherWay);
//					log.info("edge::"+edge);
//					//log.info("Adding two!");
//				}
//				else
//				{
//					((SimpleTEDB)TEDB).getNetworkGraph().addEdge(source, dest, edge);
//				}
//				
//				//log.info("Edge added:"+edge);
//				//log.info(((SimpleTEDB)TEDB).getIntraDomainLinks().toString());
//			}
//			//parseRemainingLinksFromXML(nodes);
//	 
//	 
//		} 
//		catch (Exception e)
//		{
//			log.info(UtilsFunctions.exceptionToString(e));
//		}
//	}

	private void completeTE_Information(TE_Information tE_info, String source, String dest) 
	{
		MyEdge auxEdge = new MyEdge(source, dest);
		MyEdge completEdge = interDomainLinks.get(auxEdge.hashCode());
		if ((completEdge != null)&&(completEdge.vlan != null))
		{
			tE_info.setVlanLink(true);
			tE_info.setVlan(completEdge.vlan);
			//If it has been found it will be removed so the rest can be proccessed later
			interDomainLinks.remove(completEdge.vlan);
		}
		else
		{
			tE_info.setVlanLink(false);
		}
	}

	private String queryForLinks(String ip, String port)
	{
		String response = ""; 
		try
		{
			URL topoplogyURL = new URL("http://"+ip+":"+port+topologyPathLinks);
			
			log.info("URL::"+"http://"+ip+":"+port+topologyPathLinks);
			
	        URLConnection yc = topoplogyURL.openConnection();
	        BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream()));
	        String inputLine;
	
	        while ((inputLine = in.readLine()) != null) 
	        {
	        	response = response + inputLine;
	        }
		}
		catch (Exception e)
		{
			log.info(e.toString());
		}
        return response;
	}
	
	private String queryForNodes(String ip, String port)
	{
        String response = "";
		try
		{
			URL topoplogyURL = new URL("http://"+ip+":"+port+topologyPathNodes);
			
			log.info("http://+port+topologyPathNodes:::"+"http://"+ip+":"+port+topologyPathNodes);
			
	        URLConnection yc = topoplogyURL.openConnection();
	        BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream()));
	        
	        String inputLine;	
	        while ((inputLine = in.readLine()) != null) 
	        {
	        	response = response + inputLine;
	        }
	        in.close();
		}
		catch (Exception e)
		{
			log.info(e.toString());
		}
        return response;
	}
	
	public String getInterDomainFile() 
	{
		return interDomainFile;
	}

	public void setInterDomainFile(String interDomainFile) 
	{
		this.interDomainFile = interDomainFile;
	}
	
	
	private void lock()
	{
		if (lock != null)
		{
			lock.lock();
		}
	}
	
	private void unlock()
	{
		if (lock != null)
		{
			lock.unlock();
		}
	}
}

