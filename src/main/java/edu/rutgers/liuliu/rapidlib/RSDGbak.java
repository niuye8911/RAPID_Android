package com.example.liuliu.rsdglib;
/*dummy file, just ignore this*/
import android.os.Environment;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;


public class RSDGbak implements XML,Scheme {


    static private Map<Index, Node> graph;
    LinkedList <tNode> graph_XML;

    public double budget=0;
    static Scanner sc = new Scanner(System.in);


    public void parseSchemeList(String input) { // parse the Scheme List and populate the numbers to the structure
        int i=0, j=0, pos=0, flag=0,type=0;			// i,j - use to traverse; pos,flag - use to tokenize type= type of node
        char ch1=' ', ch2= ' ';						// use to tokenize
        i=input.indexOf("rsdg-nodes") + 10; 		// jump to the nodes list
        int comment=0;								// flag for comments
        input=input.substring(i, input.length());	// trim the string before this part
        String node="";								// get each node in this string
        String edge="";								// get the line of sink and incoming sources
        String sink="";								// get the sink
        String s="";								// get the source
        Top temp_top= new Top();					// Temporary nodes
        Level temp_level = new Level();
        Basic temp_basic = new Basic();
        Index in;									//Temporary index

        //INITIALIZE THE RSDG STRUCTURE
        graph= new LinkedHashMap<Index, Node>();

	/* ********************************************************************************************/
        //NODES

        for(i=0;i<input.indexOf("rsdg-edges");i++) {
            node="";
            ch1=input.charAt(i);
            ch2=input.charAt(i+1);
            if( ch1=='S' && ch2=='_') {
                if(comment==1)
                    continue;
                pos=i;				//node start
                flag=1;				//node found
            }

            //if space occurs or closing bracket, node is complete
            else if((ch1==' ' || ch1==')' || ch1==';') && flag==1) {
                node=input.substring(pos,i);
                flag=0;
            }

            if(ch1==';')
                comment=1;

            if(ch1=='(')
                comment=0;

            type=getType(node);

            //check the type of nodes
            if(type==1) //top node
            {
                //create temp top node
                temp_top=new Top();
                temp_top.edges=new LinkedList<>();
                temp_top.serviceName="S_"+getTop(node,type);

                in = new Index();
                in.top=getTop(node,type);

                graph.put(in, temp_top);
            }

            else if(type==2) {		//level node
                temp_level=new Level();
                temp_level.edges=new LinkedList<>();

                in= new Index();
                in.top=getTop(node,type);
                in.level=getLevel(node,type);

                graph.put(in, temp_level);
            }

            else if(type==3) {		//basic node

                temp_basic=new Basic();
                temp_basic.edges=new LinkedList<>();

                in=new Index();
                in.top=getTop(node,type);
                in.level=getLevel(node,type);
                in.basic=getBasic(node,type);

                graph.put(in,temp_basic);

            }
        }

        /*********************************************************************************************/

        //EDGES
        i=input.indexOf("rsdg-edges") + 14; 	//move up in the input string
        input=input.substring(i,input.length());
        input=input.trim();

        //run till the weights part
        for(i=0;i<input.indexOf("(define rsdg-weights");i++) {

            //break the list line by line
            j=input.indexOf(")))",i)+3;

            //ignore end brackets
            if(j==2)
                break;

            //get the line
            edge=input.substring(i,j);
            edge=edge.trim();

            //sink node
            sink=edge.substring(1,edge.indexOf(' '));

            //add an extra check to prohibit adding commented code. Check for S_ in the string
            if(sink.indexOf("S_") ==-1) {
                i=i+sink.length();
                continue;
            }

            //store the sink Index
            Index sk=new Index();
            sk.top=getTop(sink,type);
            type=getType(sink);
            if(type==2)
                sk.level=getLevel(sink,type);
            else if(type==3) {
                sk.level=getLevel(sink,type);
                sk.basic=getBasic(sink,type);
            }

            //check if the node exists before adding edges
            Node n=null;
            int check=0;
            for (Index key: graph.keySet()) {
                if(sk.equals(key)) {
                    check=1;
                    break;
                }
            }
            if(check==0) {
                System.err.println("node " +sk.top +","+sk.level+ ","+ sk.basic+" doesnt exist");
                System.exit(1);
            }

            //get the node corresponding to the sink
            n=graph.get(sk);

            //get the source(s) nodes. AND edge has 1 source. OR can have multiple
            s=edge.substring(edge.indexOf("((")+2,edge.indexOf(")))"));

            //Store the edges as a list
            LinkedList<Edges> temp =new LinkedList<>();

            //For AND edge, list contains 1 entry
            if(edge.indexOf("AND")>1) {

                Edges src=new Edges();
                src.in=new Index();
                type=getType(s);
                src.in.top=getTop(s,type);
                if(type==2)
                    src.in.level=getLevel(s,type);
                else if(type==3) {
                    src.in.level=getLevel(s,type);
                    src.in.basic=getBasic(s,type);
                }

                //make a list of 1 element and add it to the list of sources
                temp.add(src);
                n.edges.add(temp);
            }

            //For OR edges, we have multiple sources
            if(edge.indexOf("OR")>1) {
                //pad the string for extraction of end source
                s=s+" ";
                Edges tmp;
                //split string to get each source
                for(String s_or : s.split(" ")) {

                    tmp=new Edges();
                    tmp.in = new Index();
                    type=getType(s_or);

                    tmp.in.top=getTop(s_or,type);
                    if(type==2)
                        tmp.in.level=getLevel(s_or,type);
                    else if(type==3) {
                        tmp.in.level=getLevel(s_or,type);
                        tmp.in.basic=getBasic(s_or,type);
                    }
                    //Mark it as OR edge
                    tmp.OR=1;
                    //add all sources in a list
                    temp.add(tmp);
                }
                //add that list to list of edges
                n.edges.add(temp);
            }

            //move up line by line j=length of current line
            i=j;
        }

        /*********************************************************************************************/
        //WEIGHTS
        i=input.indexOf("rsdg-weights") + 16;		//move up the index

        //if the useless-weights section is defined, then cut out that part from parsing
        if(input.indexOf("useless-weights") != -1)
            input=input.substring(0,input.indexOf("useless-weights"));

        input=input.substring(i,input.length());
        for(i=0;i<input.length()-1;i++) {

            ch1=input.charAt(i);
            ch2=input.charAt(i+1);
            String w;		//cost
            String v;		//value
            if(ch1=='S' && ch2=='_') {
                //Get the whole edge ex. S_2_1_1_4_1_1
                String n=input.substring(i,input.indexOf(" ", i));
                //Extract the weight
                w=input.substring(i+n.length()+1,input.indexOf(" ",i+n.length()+1));
                //Extract the value
                v=input.substring((input.indexOf(" ",i+n.length()+1)+1),
                        (input.indexOf(" ",(input.indexOf(" ",i+n.length()+1)+1))));
                //Create temporary Edge and index
                Edges temp= new Edges();
                temp.in=new Index();

                //node weights
                type=getType(n);
                if(type==3) {
                    temp.in.top=getTop(n,type);
                    temp.in.level=getLevel(n,type);
                    temp.in.basic=getBasic(n,type);

                    //get the node corresponding to the sink
                    Basic no=(Basic)graph.get(temp.in);
                    double c=Double.parseDouble(w);
                    if(v.length()!=0) {
                        double val=Double.parseDouble(v.trim());
                        no.value=val;
                    }

                    no.cost=c;
                }

                //edge weights
                else if (type==6) {
                    //Split the string in two nodes ex. S_2_1_1_4_1_1 to S_2_1_1 and S_4_1_1

                    int k=0;int count=0;

                    //get the 4th '_' which joins both
                    for(k=0;k<n.length();k++) {
                        if(n.charAt(k)=='_')
                            count++;
                        if(count==4)
                            break;
                    }
                    //construct the sink
                    String n2="S"+n.substring(k,n.length());
                    //break the whole thing to get the source
                    String n1=n.substring(0,k);

                    //source node
                    temp.in.top=getTop(n1,type);
                    temp.in.level=getLevel(n1,type);
                    temp.in.basic=getBasic(n1,type);
                    Node no =graph.get(temp.in);

                    //sink node
                    Edges si = new Edges();
                    si.in =new Index();
                    si.in.top=getTop(n2,type);
                    si.in.level=getLevel(n2,type);
                    si.in.basic=getBasic(n2,type);

                    for(int it=0;it<no.edges.size();it++) {
                        for(j=0;j<no.edges.get(it).size();j++) {
                            if(no.edges.get(it).get(j).in.top==si.in.top &&
                                    no.edges.get(it).get(j).in.level==si.in.level &&
                                    no.edges.get(it).get(j).in.basic==si.in.basic)
                                no.edges.get(it).get(j).cost=Double.parseDouble(w.substring(0, w.length()-1));
                        }
                    }
                }
            }
        }

    }

    //extract the top of a node
    public int getTop(String node, int type) {

    	/*top is between S_ and end of string (for top node)
    	or between first two '_' example: S_top_level) (for other nodes)
    	Use type to determine node type*/
        if(type==1)		//ex. S_21
            return Integer.parseInt(node.substring(2,node.length()));

        else 			// ex. S_21_2 or S_21_2_1
            return Integer.parseInt(node.substring(2,node.indexOf('_', 3)));
    }

    //extract the level of node
    public int getLevel(String node, int type) {

    	/*level is between S_top_ and end of string (for top node)
    	or between 2nd and 3rd  '_' example: S_21_level_basic) (for other nodes)
    	Use type to determine node type*/
        if(type==2)		//ex. S_21_4
            return Integer.parseInt(node.substring(node.indexOf('_', 2)+1,node.length()));

        else 			// ex. S_21_2_1
            return Integer.parseInt(node.substring(node.indexOf('_', 2)+1,node.lastIndexOf('_')));
    }

    //extract the basic of node
    public int getBasic(String node, int type) {

    	/*basic is the value after last '_'
    	type has to be 3 for this node*/
        return Integer.parseInt(node.substring(node.lastIndexOf('_')+1, node.length()));
    }

    //Determine the type of node 1- Top 2 - Level 3 - Basic
    public int getType(String node) {
        //count number of '_' to determine node type
        int type=0;
        for (int k=0;k<node.length();k++) {
            if(node.charAt(k)=='_')
                type++;
        }

        return type;
    }

    //Read the input file to a string
    public String readSchemeFile(String filename) {
        String line="",input="";
        try {
            FileReader fr= new FileReader(filename);
            BufferedReader br = new BufferedReader(fr);

            while((line = br.readLine()) != null) {
                input=input+line;
            }
            br.close();

        }
        catch(FileNotFoundException e) {
            System.out.println("Can't open:" + filename);
        }
        catch(IOException ex) {
            System.out.println( "Can't read:"+ filename);
        }
        catch(Exception e) {
            System.out.println(e.getMessage());
        }

        return input;
    }

    //Create the .lp file from the RSDG structure obtained
    public void writeSchemeFile(String filename) {
        //create the lp file with the same name as that of the scheme file
        filename=filename.substring(0,filename.indexOf("."));
        filename=filename+".lp";
        int i=0; int basic=0;
        String objective="";
        String energy="";
        String net_energy="";
        String edge_int="";


        try {
            FileWriter fileWriter = new FileWriter(filename);
            BufferedWriter bufferedWriter =  new BufferedWriter(fileWriter);

            //OBJECTIVE
            bufferedWriter.write("Maximize");
            bufferedWriter.newLine();

            for (Index key: graph.keySet()) {
                if(key.level!=0 && key.basic!=0)
                    basic++;
            }


            //run through all nodes and print basic nodes
            for (Index key: graph.keySet()) {
                //write only the basic level nodes and their values
                if(key.level!=0 && key.basic!=0) {
                    i++;
                    objective="";
                    energy="";
                    Basic node = (Basic)graph.get(key);
                    //objective is to high highest value
                    //string objective stores the val and each node
                    objective=objective+"\t"+ node.value +
                            " S_"+key.top+"_"+key.level+"_"+key.basic + "\n";

                    //this is the final energy(cost) constraint
                    energy=energy+"\t"+ node.cost +
                            " S_"+key.top+"_"+key.level+"_"+key.basic + "\n";
                    // bufferedWriter.newLine();
                    //append the + after each node, except last
                    if(i!=basic) {
                        objective=objective +"+ ";
                        energy = energy + "+ ";
                    }

                    bufferedWriter.write(objective);
                    net_energy+=energy;

                    LinkedList<Edges> edgeList;
                    for(int j=0;j<node.edges.size();j++) {
                        edgeList= node.edges.get(j);
                        for(Edges edge: edgeList) {
                            objective="";
                            energy="";
                            if(edge.cost!=0) {
                                energy = "\t"+edge.cost+ " " +
                                        "S_"+key.top+"_"+key.level+"_"+key.basic +
                                        "_"+edge.in.top+"_"+edge.in.level+"_"+edge.in.basic + "\n";
                                objective="\t"+"0.0"+ " " +
                                        "S_"+key.top+"_"+key.level+"_"+key.basic +
                                        "_"+edge.in.top+"_"+edge.in.level+"_"+edge.in.basic + "\n";
                                if(i!=basic) {
                                    objective+="+ ";
                                    energy+="+";
                                }
                                bufferedWriter.write(objective);
                                net_energy+=energy;
                            }
                        }
                    }
                }
            }

            bufferedWriter.newLine();

            //CONSTRAINTS
            int c =1;								//constraint number
            String constraint="";
            bufferedWriter.write("Subject To");
            bufferedWriter.newLine();

            /*
             * Generate constraints as follows -
             * 1. All basic nodes in a service - levels/implementations of a service =0
             * 2. All levels in a service - top node of service =0
             * 3. Sink - all weighted edges = 0
             * 4. AND Edges: Source - Sink >= 0
             * 5. OR  Edges: All Sources - Sink >=0
             * 6. Weighted Edge - Source <=0
             * 7. Overall Energy constraint
             */

            //find all basic and level nodes associated with a top node
            for (Index key: graph.keySet()) {
                //top node
                if(key.basic==0 && key.level==0) {

                    //For each top node, store the basic and level nodes in 2 lists
                    LinkedList<Index> level_nodes=new LinkedList<>();
                    LinkedList<Index> basic_nodes=new LinkedList<>();

                    //run through the graph to find nodes within the service
                    for (Index index: graph.keySet()) {
                        constraint="";
                        if(index.top==key.top) {		//belongs to same service
                            Index temp=new Index();

                            //level nodes
                            if(index.top!=0 && index.level!=0 && index.basic==0) {
                                temp.top=index.top;
                                temp.level=index.level;
                                level_nodes.add(temp);
                            }

                            //basic nodes
                            else if(index.top!=0 && index.level!=0 && index.basic!=0) {
                                temp.top=index.top;
                                temp.level=index.level;
                                temp.basic=index.basic;
                                basic_nodes.add(temp);
                            }
                        }
                    }

                    //1. All basic nodes in a service - levels/implementations of a service =0
                    for(i=0;i<level_nodes.size();i++) {			//for each level, get the basic nodes in that level
                        constraint="c"+c+": ";
                        c++;
                        for(int j=0;j<basic_nodes.size();j++) {
                            //basic nodes of same level
                            if(basic_nodes.get(j).level==level_nodes.get(i).level) {
                                constraint=constraint+ " S_" + basic_nodes.get(j).top +
                                        "_" + basic_nodes.get(j).level +
                                        "_" + basic_nodes.get(j).basic +
                                        " +";
                            }
                        }

                        //generate string
                        constraint=constraint.substring(0,constraint.lastIndexOf(' '));
                        constraint =constraint + " - S_" + level_nodes.get(i).top
                                + "_" + level_nodes.get(i).level + " = 0";

                        bufferedWriter.write(constraint);
                        bufferedWriter.newLine();

                    }

                    //2. All levels in a service - top node of service =0
                    constraint="c"+c+": ";
                    c++;
                    for (i=0;i<level_nodes.size();i++) {
                        constraint=constraint+ " S_" + level_nodes.get(i).top +
                                "_" + level_nodes.get(i).level +
                                " +";
                    }

                    //generate string
                    constraint=constraint.substring(0,constraint.lastIndexOf(' '));
                    constraint=constraint + " - S_" + key.top + " = 0";

                    bufferedWriter.write(constraint);
                    bufferedWriter.newLine();

                    int e=0;
                    //3. Sink - all weighted edges to it  = 0
                    //run through basic nodes
                    for(i=0;i<basic_nodes.size();i++) {
                        constraint="c"+c+": ";
                        //flag
                        e=0;
                        Node n=graph.get(basic_nodes.get(i));
                        if(n.edges.size()!=0) {
                            constraint+= " S_" + basic_nodes.get(i).top + "_" +
                                    basic_nodes.get(i).level + "_" +
                                    basic_nodes.get(i).basic + " - ";

                            for(int j=0;j<n.edges.size();j++) {
                                for(int k=0;k<n.edges.get(j).size();k++) {
                                    if(n.edges.get(j).get(k).cost!=0) {
                                        e=1;
                                        constraint+=" S_" + basic_nodes.get(i).top + "_" +
                                                basic_nodes.get(i).level + "_" +
                                                basic_nodes.get(i).basic + "_" +
                                                n.edges.get(j).get(k).in.top +"_" +
                                                n.edges.get(j).get(k).in.level +"_" +
                                                n.edges.get(j).get(k).in.basic +" -" ;
                                    }
                                }
                            }

                            if(e==1) {
                                c++;
                                constraint = constraint.substring(0, constraint.lastIndexOf('-'));
                                constraint+= " = 0";
                                bufferedWriter.write(constraint);
                                bufferedWriter.newLine();
                            }
                        }
                    }

                    //4. AND edges : Source - sink >=0
                    //Run through the basic nodes
                    for(i=0;i<basic_nodes.size();i++) {
                        //Get the basic node
                        Node n=graph.get(basic_nodes.get(i));
                        //run through the edges
                        for(int j=0;j<n.edges.size();j++) {
                            // size =1 means the list represents AND edge
                            if(n.edges.get(j).size()==1) {
                                constraint="c"+c+": ";
                                constraint += " S_"+ n.edges.get(j).get(0).in.top + "_"
                                        + n.edges.get(j).get(0).in.level + "_"
                                        + n.edges.get(j).get(0).in.basic + " - "
                                        + "S_" +  basic_nodes.get(i).top + "_"
                                        + basic_nodes.get(i).level + "_"
                                        + basic_nodes.get(i).basic + " >= 0" ;
                                bufferedWriter.write(constraint);
                                bufferedWriter.newLine();
                                c++;

                            }

                            // 5. OR  edges : All sources - sink >=0
                            else {
                                //store group of OR edges in or_edges
                                LinkedList <Edges> or_edges = n.edges.get(j);
                                constraint="c"+c+": ";
                                c++;
                                // Sum of all OR edges
                                for(int k=0;k<or_edges.size();k++) {
                                    constraint += " S_" + or_edges.get(k).in.top + "_"
                                            + or_edges.get(k).in.level + "_"
                                            + or_edges.get(k).in.basic + " + " ;
                                }

                                //Truncate extra '+' at the end
                                constraint=constraint.substring(0, constraint.lastIndexOf('+'));

                                constraint+= " - " + "S_" +  basic_nodes.get(i).top + "_"
                                        + basic_nodes.get(i).level + "_"
                                        + basic_nodes.get(i).basic + " >= 0" ;
                                bufferedWriter.write(constraint);
                                bufferedWriter.newLine();

                            }

                            // 6. Weighted Edge - Source <=0
                            for(int k=0;k<n.edges.get(j).size();k++) {
                                //if the edge is weighted
                                if(n.edges.get(j).get(k).cost!=0) {
                                    constraint="c"+c+": ";
                                    c++;
                                    String source = n.edges.get(j).get(k).in.top + "_"
                                            + n.edges.get(j).get(k).in.level + "_"
                                            + n.edges.get(j).get(k).in.basic;
                                    constraint +=  " S_" +  basic_nodes.get(i).top + "_"
                                            + basic_nodes.get(i).level + "_"
                                            + basic_nodes.get(i).basic + "_"
                                            + source + " - " +
                                            "S_" + source +
                                            " <= 0" ;
                                    bufferedWriter.write(constraint);
                                    bufferedWriter.newLine();
                                }
                            }
                        }
                    }
                }
            }

            //7.overall constraint with energy
            constraint="\nc"+c+":  " + net_energy.trim() + "\n- energy = 0\n";
            bufferedWriter.write(constraint);
            c++;


            //Set energy constraint
            System.out.println("Enter budget");
            budget=sc.nextDouble();

            constraint="\nc" + c+ ": energy <= " + budget +"\n";
            bufferedWriter.write(constraint);

            //BOUNDS
            bufferedWriter.newLine();
            bufferedWriter.write("Bounds");
            bufferedWriter.newLine();
            String bounds="";

            //print the nodes
            for (Index key: graph.keySet()) {
                bounds="S_" + key.top;
                if(key.level!=0)
                    bounds=bounds+ "_" +  key.level;

                if(key.basic!=0)
                    bounds= bounds + "_" +  key.basic;

                bounds = bounds + " <= 1";

                bufferedWriter.write(bounds);
                bufferedWriter.newLine();

                //print the weighted edges
                if(graph.get(key).edges.size()!=0) {
                    //if edges exist, check for weighted edges
                    Basic node = (Basic)graph.get(key);
                    LinkedList<Edges> edgeList;
                    for(int j=0;j<node.edges.size();j++) {
                        edgeList= node.edges.get(j);
                        for(Edges edge: edgeList) {
                            if(edge.cost!=0) {
                                bounds="";
                                bounds="S_"+key.top+"_"+key.level+"_"+key.basic +
                                        "_"+edge.in.top+"_"+edge.in.level+"_"+edge.in.basic +
                                        " <= 1";
                                bufferedWriter.write(bounds);
                                bufferedWriter.newLine();
                            }
                        }
                    }
                }
            }

            //INTEGERS
            bufferedWriter.newLine();
            bufferedWriter.write("Integers");
            bufferedWriter.newLine();
            String intg="";
            for (Index key: graph.keySet()) {
                intg="S_" + key.top;
                if(key.level!=0)
                    intg=intg+ "_" +  key.level;

                if(key.basic!=0)
                    intg= intg + "_" +  key.basic;

                bufferedWriter.write(intg);
                bufferedWriter.write(edge_int);
                intg="";
                bufferedWriter.newLine();
                if(key.level==0 || key.basic==0)
                    continue;
                Basic node = (Basic)graph.get(key);
                if(node.edges.size()!=0) {
                    for(int j=0;j<node.edges.size();j++) {
                        LinkedList<Edges> edgeList;
                        edgeList= node.edges.get(j);
                        for(Edges edge: edgeList) {
                            if(edge.cost!=0) {
                                bufferedWriter.write("S_"+key.top+"_"+key.level+"_"+key.basic +
                                        "_"+edge.in.top+"_"+edge.in.level+"_"+edge.in.basic);
                                bufferedWriter.newLine();
                            }
                        }
                    }
                }
            }

            bufferedWriter.newLine();
            bufferedWriter.write("End");

            //close the bW.
            bufferedWriter.close();
        }
        catch(IOException ex) {
            System.out.println(
                    "Error writing to file '"
                            + filename + "'");
            // Or we could just do this:
            // ex.printStackTrace();
        }
    }

    public static Document loadXMLFromString(String xml) throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(xml));
        return builder.parse(is);
    }

    public void parseXMLFile (String filecontent) {
        Log.d("RSDG","preparing to parse the file");
        int i=0,j=0,k=0;
        //Main list of services that will hold the whole structure
        graph_XML = new LinkedList <> ();

        //Read xml using document builder
        try {
            //use document builder to parse the xml file
            //File file= new File (ctx.getObbDir(),filename);
           /* if(file!=null)Log.d("RSDG","filefound");
            else Log.d("RSDG","file not found");*/
            DocumentBuilderFactory dbf =
                    DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Log.d("RSDG","parsing");
            InputSource is = new InputSource(new StringReader(filecontent));
            Document dom =db.parse(is);
            Element doc =dom.getDocumentElement();

            //get list of all services in top
            NodeList top = doc.getElementsByTagName("service");
            Log.d("RSDG","top nodes done");
            Log.d("RSDG","Topnode:"+top.getLength());
            //run through all top nodes/ services
            for (i=0;i<top.getLength();i++) {
                //get each top node element
                Element t = (Element) top.item(i);
                //temporary top node
                tNode ttemp = new tNode();

                //store service name
                ttemp.name=t.getElementsByTagName("servicename").item(0).getTextContent();

                //get the list of levels in that service
                NodeList level = t.getElementsByTagName("servicelayer");

                //if levels exist, initialize the list in data structure
                if(level.getLength()>0)
                    ttemp.levelnodes= new LinkedList<> ();

                //run through all levels in that service
                for(j=0; j<level.getLength();j++) {
                    //store each level element
                    Element l = (Element) level.item(j);
                    //temp level node
                    lNode ltemp = new lNode();

                    //get list of basic nodes in that level of that service
                    NodeList basic= l.getElementsByTagName("basicnode");

                    //Naming convention of level nodes will be Topnode_Levelnumber
                    ltemp.name=ttemp.name+"_"+(j+1);

                    // if basic nodes exists in that level, initialize the list in DS
                    if(basic.getLength()>0)
                        ltemp.basicnodes=new LinkedList<> ();

                    //run through basic nodes
                    for(k=0;k<basic.getLength();k++) {

                        //temp basic node
                        bNode btemp = new bNode();
                        //get the xml element for each basic node
                        Element b = (Element) basic.item(k);

                        //store the basic node name
                        btemp.name =b.getElementsByTagName("nodename").item(0).getTextContent();

                        //store the basic node cost
                        btemp.cost =Double.parseDouble(b.getElementsByTagName("nodecost").item(0).getTextContent());
                        //btemp.value=Double.parseDouble(b.getElementsByTagName("nodevalue").item(0).getTextContent());
                        btemp.value = 0;
                        //get the AND edges in  basic node
                        NodeList and_edges = b.getElementsByTagName("and");

                        //get the OR edges in the basic node
                        NodeList or_edges = b.getElementsByTagName("or");

                        //instantiate a list of edges
                        btemp.Edges=new LinkedList <>();

                        //if AND edges exist add them to the list
                        if(and_edges.getLength()>0) {
                            //run through the AND edges
                            for(int m=0; m<and_edges.getLength();m++) {
                                //For each and edge create a list (for AND, each list will have 1 element)
                                LinkedList<Edge> and = new LinkedList<>();
                                Element a = (Element) and_edges.item(m);
                                //create a temp edge
                                Edge temp = new Edge();
                                //get the name of source node ( just 1  AND edge )
                                temp.name=a.getElementsByTagName("name").item(0).getTextContent();
                                //get the edge weight
                                temp.weight=Double.parseDouble
                                        (a.getElementsByTagName("weight").item(0).getTextContent());
                                //Add single node in the list
                                and.add(temp);
                                //add that list to list of edges of a basic node
                                btemp.Edges.add(and);
                            }
                        }

                        //Add or edges now
                        if(or_edges.getLength()>0) {
                            //run through the sets of OR edges.
    						/*Each or tag will have multiple name-weight pairs and
    						 * each basic node can have multiple groups of OR edges
    						 */
                            for(int m=0; m<or_edges.getLength();m++) {
                                //For each OR edge create a list (for OR, each list can have >1 element(s))
                                Element o = (Element) or_edges.item(m);

                                //Get the list of names and weights
                                NodeList names = o.getElementsByTagName("name");
                                NodeList weights =o.getElementsByTagName("weight");
                                //check that the name-weight pairs are consistent
                                if(names.getLength()!= weights.getLength()) {
                                    System.out.println("Error in parsing:edge weight/name missing");
                                    System.exit(1);
                                }

                                LinkedList<Edge> or = new LinkedList<>();
                                //or edges exist
                                if(names.getLength()>0) {
                                    //add all the or edges
                                    for(int p=0;p<names.getLength();p++) {
                                        Edge temp = new Edge();
                                        temp.name=names.item(p).getTextContent();
                                        temp.weight= Double.parseDouble(
                                                weights.item(p).getTextContent());
                                        or.add(temp);
                                    }

                                    btemp.Edges.add(or);
                                }
                            }

                        }

                        //add basic node in the level
                        ltemp.basicnodes.add(btemp);
                    }

                    ttemp.levelnodes.add(ltemp);
                }

                //add the service in the graph structure
                graph_XML.add(ttemp);
            }

        } catch (ParserConfigurationException pce) {
            System.out.println(pce.getMessage());
        } catch (SAXException se) {
            System.out.println(se.getMessage());
        } catch (IOException ioe) {
            System.err.println(ioe.getMessage());
        }

        //display
    	/*
    	for(i=0;i<graph_XML.size();i++) {
    		System.out.println("Service: "+ graph_XML.get(i).name);
    		for(j=0;j<graph_XML.get(i).levelnodes.size();j++) {
    			System.out.println(graph_XML.get(i).levelnodes.get(j).name);
    			for(k=0;k<graph_XML.get(i).levelnodes.get(j).basicnodes.size();k++) {
    				System.out.print(graph_XML.get(i).levelnodes.get(j).basicnodes.get(k).name +
    						"(" + graph_XML.get(i).levelnodes.get(j).basicnodes.get(k).cost + "), ");
    				System.out.print("\nEdges:");
    				for(int m=0;m<graph_XML.get(i).levelnodes.get(j).basicnodes.get(k).Edges.size();m++) {
    						System.out.print(graph_XML.get(i).levelnodes.get(j).basicnodes.get(k).Edges.get(m).toString()+",");
    				}
    			}
    			System.out.println("");
    		}
    		System.out.println("");
    	}*/

    }

    public void writeXMLFile(String filename) throws IOException {
        //create the lp file with the same name as that of the xml file
        filename=filename+".lp";
        File output = Environment.getExternalStorageDirectory();
        String sdcardPath = output.getPath();
        Log.d("XMLwriter","path="+sdcardPath);
        File file = new File(sdcardPath + "/"+filename);
        file.createNewFile();
        int i=0;
        String objective="";
        String energy="";
        try {
            FileWriter fileWriter = new FileWriter(file);
            BufferedWriter bufferedWriter =  new BufferedWriter(fileWriter);

            //OBJECTIVE
            bufferedWriter.write("Maximize");
            bufferedWriter.newLine();

            for(i=0;i<graph_XML.size();i++) {
                for(int j=0;j<graph_XML.get(i).levelnodes.size();j++) {
                    for(int k=0;k<graph_XML.get(i).levelnodes.get(j).basicnodes.size();k++) {
                        bNode temp=graph_XML.get(i).levelnodes.get(j).basicnodes.get(k);
                        objective+="\t"+temp.value+" " + temp.name + "\n+";
                        energy+="\t"+temp.cost+" " + temp.name + "\n+";
                        if(temp.Edges.size()!=0) {
                            int size=temp.Edges.size();
                            for(int l=0;l<size;l++) {
                                for(int m=0;m<temp.Edges.get(l).size();m++) {
                                    if(temp.Edges.get(l).get(m).weight!=0) {
                                        objective+="\t"+"0.0"
                                                +" " + temp.name+"$"+temp.Edges.get(l).get(m).name + "\n+";
                                        energy+="\t"+temp.Edges.get(l).get(m).weight
                                                +" " + temp.name+"$"+temp.Edges.get(l).get(m).name + "\n+";            						 }
                                }
                            }
                        }
                    }
                }
            }

            objective=objective.substring(0,objective.lastIndexOf('+'));
            bufferedWriter.write(objective);


             /*
             * Generate constraints as follows -
             * 1. All levels in a service - top node of service =0
             * 2. All basic nodes in a service - levels/implementations of a service =0
             * 3. Sink - all weighted edges = 0
             * 4. AND Edges: Source - Sink >= 0
             * 5. OR  Edges: All Sources - Sink >=0
             * 6. Weighted Edge - Source <=0
             * 7. Overall Energy constraint
              */

            //CONSTRAINTS
            bufferedWriter.newLine();
            bufferedWriter.write("Subject To");
            bufferedWriter.newLine();

            int c=1;
            String constraint="";


            //1. All levels in a service - top node of service =0
            for(i=0;i<graph_XML.size();i++) {
                constraint="c"+c+": ";
                for(int j=0;j<graph_XML.get(i).levelnodes.size();j++) {
                    constraint+= graph_XML.get(i).levelnodes.get(j).name+" + ";
                }
                constraint=constraint.substring(0,constraint.lastIndexOf('+'));
                constraint+= "- " + graph_XML.get(i).name + " = 0";
                bufferedWriter.write(constraint);
                bufferedWriter.newLine();
                c++;
            }

            bufferedWriter.newLine();
            //2. All basic nodes in a service - levels/implementations of a service =0
            for(i=0;i<graph_XML.size();i++) {
                for(int j=0;j<graph_XML.get(i).levelnodes.size();j++) {
                    constraint="c"+c+": ";
                    for(int k=0;k<graph_XML.get(i).levelnodes.get(j).basicnodes.size();k++) {
                        constraint+= graph_XML.get(i).levelnodes.get(j).basicnodes.get(k).name+" + ";
                    }
                    constraint=constraint.substring(0,constraint.lastIndexOf('+'));
                    constraint+= "- " + graph_XML.get(i).levelnodes.get(j).name + " = 0";
                    bufferedWriter.write(constraint);
                    bufferedWriter.newLine();
                    c++;

                }
            }

            //3. Sink - all weighted edges = 0
            //4. AND Edges: Source - Sink >= 0
            //5. OR  Edges: All Sources - Sink >=0
            //6. Weighted Edge - Source <=0
            bufferedWriter.newLine();
            String constraint4="";
            //run through the graph
            for(i=0;i<graph_XML.size();i++) {
                for(int j=0;j<graph_XML.get(i).levelnodes.size();j++) {
                    for( int k=0;k<graph_XML.get(i).levelnodes.get(j).basicnodes.size();k++) {
                        //get the basic node
                        bNode temp=graph_XML.get(i).levelnodes.get(j).basicnodes.get(k);
                        constraint4="";
                        //run through the edges
                        for(int l=0;l<temp.Edges.size();l++) {
                            for(int m=0;m<temp.Edges.get(l).size();m++) {
                                if(temp.Edges.get(l).get(m).weight!=0) {

                                    //Sink - all weighted edges = 0
                                    constraint4+=temp.name+"$"+temp.Edges.get(l).get(m).name
                                            +" - ";
                                    //Weighted Edge - Source <=0
                                    bufferedWriter.write("c" + c +": " +
                                            temp.name+"$"+temp.Edges.get(l).get(m).name +
                                            " - " + temp.Edges.get(l).get(m).name + " <= 0");
                                    bufferedWriter.newLine();
                                    c++;
                                }

                                //AND Edges: Source - Sink >= 0
                                if(temp.Edges.get(l).size()==1) {
                                    bufferedWriter.write("c"+c+": "+temp.Edges.get(l).get(0).name
                                            + " - " + temp.name +" >= 0");
                                    c++;
                                    bufferedWriter.newLine();
                                }
                            }

                            //OR  Edges: All Sources - Sink >=0
                            if(temp.Edges.get(l).size()>1) {
                                String constraint6="";
                                for(int m=0;m<temp.Edges.get(l).size();m++) {
                                    constraint6+=temp.name+"$" +
                                            temp.Edges.get(l).get(m).name +" + ";
                                }
                                constraint6=constraint6.substring(0,constraint6.lastIndexOf('+'));
                                constraint6+= " - " +temp.name + " >= 0";
                                constraint6="c"+c+": " + constraint6;
                                c++;
                                bufferedWriter.write(constraint6);
                                bufferedWriter.newLine();
                            }
                        }
                        if(constraint4.indexOf('-') >0 ) {
                            constraint4=constraint4.substring(0,constraint4.lastIndexOf('-'));
                            constraint4="c" +c +": " +  temp.name + " - "  + constraint4 +" = 0";
                            c++;
                            bufferedWriter.write(constraint4);
                            bufferedWriter.newLine();
                        }
                    }
                }
            }


            //7. Overall Energy constraint
            budget=999;
            bufferedWriter.newLine();
            bufferedWriter.write("c"+c+": "+energy.trim().substring(0,energy.trim().lastIndexOf('+'))
                    + "- energy" +" = 0");
            bufferedWriter.newLine();
            c++;
            bufferedWriter.newLine();
            bufferedWriter.write("c"+c+": " + "energy <= "+budget);
            bufferedWriter.newLine();

            //Bounds
            bufferedWriter.newLine();
            bufferedWriter.write("Bounds");
            bufferedWriter.newLine();
            String integers="";
            for(i=0;i<graph_XML.size();i++) {
                bufferedWriter.write(graph_XML.get(i).name+ " <= 1");
                integers+=graph_XML.get(i).name+"\n";
                bufferedWriter.newLine();
                for(int j=0;j<graph_XML.get(i).levelnodes.size();j++) {
                    bufferedWriter.write(graph_XML.get(i).levelnodes.get(j).name+ " <= 1");
                    integers+=graph_XML.get(i).levelnodes.get(j).name+"\n";
                    bufferedWriter.newLine();
                    for(int k=0;k<graph_XML.get(i).levelnodes.get(j).basicnodes.size();k++) {
                        bNode temp=graph_XML.get(i).levelnodes.get(j).basicnodes.get(k);
                        integers+=temp.name+"\n";
                        bufferedWriter.write(temp.name+ " <= 1");
                        bufferedWriter.newLine();
                        for(int l=0;l<temp.Edges.size();l++) {
                            for(int m=0;m<temp.Edges.get(l).size();m++){
                                if(temp.Edges.get(l).get(m).weight!=0) {
                                    integers+=temp.name+"$"+
                                            temp.Edges.get(l).get(m).name+"\n";
                                    bufferedWriter.write(temp.name+"$" +
                                            temp.Edges.get(l).get(m).name+ " <= 1");
                                    bufferedWriter.newLine();
                                }
                            }
                        }
                    }
                }
            }

            //Integers
            bufferedWriter.newLine();
            bufferedWriter.write("Integers");
            bufferedWriter.newLine();
            bufferedWriter.write(integers);

            bufferedWriter.newLine();
            bufferedWriter.write("End");
            bufferedWriter.close();

        }


        catch(IOException ex) {
            System.out.println(
                    "Error writing to file '"
                            + filename + "'");
        }

    }

    public void updateMissionValue(String serviceName, int value, boolean exp, int xml) {

        //flag if service is found
        int found=0;

        //TODO: Implement scheme part
        if(xml==1) {
            //store the service if found
            tNode service=null ;
            //run through the service names
            for(int i=0;i< graph_XML.size();i++) {
                //found
                if((graph_XML.get(i).name.compareToIgnoreCase(serviceName))==0)  {
                    //store the service
                    service=graph_XML.get(i);
                    found=1;
                    break;
                }
            }

            //service doesn't exist
            if(found==0) {
                System.out.println("Service " +serviceName + "not found");
            }

            //service found
            else {
                //get all the levels in the service
                LinkedList<lNode> levels = service.levelnodes;
			   /*
			    * We need to set mission values to all basic nodes in each level
			    */
                for(int i=0;i<levels.size();i++) {

                    //now, depending on exponential flag, change the value of input mission value
                    //linear decrease
                    if(exp==false) {
                        value=value/levels.size()* (levels.size()-i);
                    }

                    //TODO: Exponential decrease

                    //set the same value to all basic nodes in a level
                    for(int j=0;j<levels.get(i).basicnodes.size();j++) {
                        levels.get(i).basicnodes.get(j).value=value;
                    }
                }
            }
        }

        //Scheme
        else if(xml==0) {

            Top service= null;
            //check if it's a top node
            if(serviceName.length()!=3) {
                System.out.println("Node is not a service");
                return;
            }
            //store the service if found
            Index in=new Index();
            in.basic=in.level=0;
            in.top=getTop(serviceName,1);
            service=(Top)graph.get(in);

            if(service==null) {
                System.out.println("Invalid node");
                return;
            }

            //get the number of levels in that service
            int num_of_levels=0;
            for (Index index : graph.keySet()) {

                //same service, but level node
                if(index.top== in.top && index.level >0 && index.basic==0 ) {
                    if(index.level > num_of_levels)
                        num_of_levels=index.level;
                }
            }
            //now set the value to the basic nodes in that level

            for(Index index : graph.keySet()) {

                //same service and it's a basic node
                if(index.top == in.top  && index.basic>0) {
                    Basic temp = (Basic)graph.get(index);
                    //Linear
                    if(exp==false)
                        temp.value= value/num_of_levels * (num_of_levels-index.level+1);
                    //TODO: Exponential
                }
            }


        }
    }

    public void updateCost(String basic, int cost, int xml) {

        //flag to check basic node exists
        int found =0;

        //Xml part
        if(xml==1) {
            //store the basic node if found
            bNode temp = null;
            for(int i=0;i<graph_XML.size();i++) {
                for(int j=0;j<graph_XML.get(i).levelnodes.size();j++) {
                    for(int k=0;k<graph_XML.get(i).levelnodes.get(j).basicnodes.size();k++) {
                        if(graph_XML.get(i).levelnodes.get(j).basicnodes.get(k).name.compareTo(basic)==0) {
                            found=1;
                            temp=graph_XML.get(i).levelnodes.get(j).basicnodes.get(k);
                            break;
                        }
                    }
                }
            }

            if(found==0) {
                System.out.println("basic node not found");
            }

            else {
                temp.cost=cost;
            }

        }

        //Scheme
        else if (xml==0) {
            //check validity
            if(basic.length()!=7) {
                System.out.println("Not a basic node");
                return;
            }
            //extract the top,level,basic for the basic node (type =2)
            Index index=new Index();
            index.top=getTop(basic,3);
            index.level=getLevel(basic,3);
            index.basic=getLevel(basic,3);


            Basic temp = (Basic)graph.get(index);
            //node not found
            if(temp==null) {
                System.out.println("basic node not found");
                return;
            }

            //if found, update cost
            temp.cost=cost;
            graph.put(index,temp);
        }

    }



}
