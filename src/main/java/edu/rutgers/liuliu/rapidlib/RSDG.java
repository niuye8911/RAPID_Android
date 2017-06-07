package com.example.liuliu.rsdglib;

import android.content.Context;
import android.media.audiofx.EnvironmentalReverb;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


/**
 * compiles RSDG in XML to runtime data structure(RSDG)
 */
public class RSDG implements XML, Scheme {

    /*Use this as a user defined structure that holds name of
     *a node and whether it is selected by the heuristic
     */
    public class nodeList {
        public String name;
        public int selected;
    }

    ;

    //Runtime DS for Scheme
    static private Map<Index, Node> graph;

    //Runtime DS for XML
    LinkedList<XML.tNode> graph_XML;

    //Net energy/budget provided by the user
    public double budget = 0;

    static Scanner sc = new Scanner(System.in);

    /*
     * This function reads the scheme (.ss) file provided by the user
     * and returns its contents in a string
     */
    @Override
    public String readSchemeFile(String filename) {
        String line = "", input = "";
        try {
            FileReader fr = new FileReader(filename);
            BufferedReader br = new BufferedReader(fr);

            //Read line by line
            while ((line = br.readLine()) != null) {
                input = input + line;
            }
            br.close();

        } catch (FileNotFoundException e) {
            System.out.println("Can't open:" + filename);
        } catch (IOException ex) {
            System.out.println("Can't read:" + filename);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return input;
    }

    /*
     * This function parses the string obtained on
     * reading the scheme file. The contents are stored in
     * a runtime structure - a hash-map
     */
    @Override
    public void parseSchemeList(String input) {
        int i = 0, j = 0, pos = 0, flag = 0, type = 0;            // i,j - use to traverse; pos,flag - use to tokenize type= type of node
        char ch1 = ' ', ch2 = ' ';                        // use to tokenize
        i = input.indexOf("rsdg-nodes") + 10;        // jump to the nodes list
        int comment = 0;                                // flag for comments
        input = input.substring(i, input.length());    // trim the string before this part
        String node = "";                                // get each node in this string
        String edge = "";                                // get the line of sink and incoming sources
        String sink = "";                                // get the sink
        String s = "";                                // get the source
        Top temp_top = new Top();                    // Temporary nodes
        Level temp_level = new Level();
        Basic temp_basic = new Basic();
        Index in;                                    //Temporary index

        //INITIALIZE THE RSDG STRUCTURE
        graph = new LinkedHashMap<Index, Node>();

		/*
         * The scheme file contains 3 main parts - Nodes, Edges and Weights
		 * The Nodes part defines all the nodes(top, level and basic) in the RSDG
		 * The Edges part defines all the edges(AND or OR edges) in the RSDG
		 * The Weights part defines the weight/cost of each node and certain edges
		 */

		/*
		 * Parse through the input to first get the nodes and get a barebone structure
		 * of the RSDG. After that populate the weights and for each node store the associated
		 * sources that form an edge with the node
		 */
        /**********************************************************************************************/
        //NODES

        for (i = 0; i < input.indexOf("rsdg-edges"); i++) {
            node = "";
            ch1 = input.charAt(i);
            ch2 = input.charAt(i + 1);

            //Node in scheme starts with "S_"
            if (ch1 == 'S' && ch2 == '_') {

                //the comment variable is a flag to skip the comments
                if (comment == 1)
                    continue;

                pos = i;                //pos: node start
                flag = 1;                //flag=1: node found
            }

            //if space or closing bracket or ';' occurs, node is complete
            else if ((ch1 == ' ' || ch1 == ')' || ch1 == ';') && flag == 1) {
                //fetch the node as a substring
                node = input.substring(pos, i);

                //once the node is found, mark the node as 0
                flag = 0;
            }

            //semi-colon marks the start of a comment
            if (ch1 == ';')
                comment = 1;

            //'(' marks the start of a node name in scheme
            if (ch1 == '(')
                comment = 0;

            //get the type of node from its name: Top[0], level[1] or basic[2]
            type = getType(node);


            if (type == 1) //top node
            {
                //create temp top node and temp list for edges
                temp_top = new Top();
                temp_top.edges = new LinkedList<>();

                //eg. top node name - S_1
                temp_top.serviceName = "S_" + getTop(node, type);

                //temp. index node
                in = new Index();

                //extract the service number from node name and type
                in.top = getTop(node, type);

                //insert the node in the graph
                graph.put(in, temp_top);
            } else if (type == 2) {        //level node
                temp_level = new Level();
                temp_level.edges = new LinkedList<>();

                in = new Index();
                //extract top and level resp. from the name
                in.top = getTop(node, type);
                in.level = getLevel(node, type);

                graph.put(in, temp_level);
            } else if (type == 3) {        //basic node

                temp_basic = new Basic();
                temp_basic.edges = new LinkedList<>();

                //extract top, level and basic from name
                in = new Index();
                in.top = getTop(node, type);
                in.level = getLevel(node, type);
                in.basic = getBasic(node, type);

                graph.put(in, temp_basic);

            }
        }

        //Once the nodes are added in the graph, find the edges in the graph
        /*********************************************************************************************/

        //EDGES
        i = input.indexOf("rsdg-edges") + 14;    //move up in the input string
        input = input.substring(i, input.length());
        input = input.trim();

        //run till the weights part
        for (i = 0; i < input.indexOf("(define rsdg-weights"); i++) {

            //break the list line by line (too input specific!)
            j = input.indexOf(")))", i) + 3;

            //ignore end brackets
            //for end brackets, j will be -1 + 3 = 2 since indexOf will give 0
            if (j == 2)
                break;

            //get the line
            edge = input.substring(i, j);
            edge = edge.trim();

            //sink node eg. (S_2_1_1_4_1_1 2) has sink S_2_1_1 and source S_4_1_1 with wt = 2
            //sink = start to 1st space
            sink = edge.substring(1, edge.indexOf(' '));

            //add an extra check to prohibit adding commented code. Check for S_ in the string
            if (sink.indexOf("S_") == -1) {
                //skip that length from parsing
                i = i + sink.length();
                continue;
            }

            //store the sink Index
            Index sk = new Index();
            sk.top = getTop(sink, type);
            type = getType(sink);
            if (type == 2)
                sk.level = getLevel(sink, type);
            else if (type == 3) {
                sk.level = getLevel(sink, type);
                sk.basic = getBasic(sink, type);
            }

            //check if the node exists before adding edges
            Node n = null;
            int check = 0;
            for (Index key : graph.keySet()) {
                if (sk.equals(key)) {
                    check = 1;
                    break;
                }
            }

            //node not found. Hence we can't add a edge for it
            if (check == 0) {
                System.err.println("node " + sk.top + "," + sk.level + "," + sk.basic + " doesnt exist");
                System.exit(1);
            }

            //get the node corresponding to the sink
            n = graph.get(sk);

            //get the source(s) nodes. AND edge has 1 source. OR can have multiple
            s = edge.substring(edge.indexOf("((") + 2, edge.indexOf(")))"));

            //Store the edges as a list
            LinkedList<Edges> temp = new LinkedList<>();

            //For AND edge, list contains 1 entry. Determine from number of "AND" in
            //input if it's a AND or OR edge
            if (edge.indexOf("AND") > 1) {

                Edges src = new Edges();
                src.in = new Index();
                //get the type of node
                type = getType(s);
                src.in.top = getTop(s, type);
                if (type == 2)
                    src.in.level = getLevel(s, type);
                else if (type == 3) {
                    src.in.level = getLevel(s, type);
                    src.in.basic = getBasic(s, type);
                }

                //make a list of 1 element and add it to the list of sources
                temp.add(src);
                n.edges.add(temp);
            }

            //For OR edges, we have multiple sources
            if (edge.indexOf("OR") > 1) {
                //pad the string for extraction of end source
                s = s + " ";
                Edges tmp;
                //split string to get each source
                for (String s_or : s.split(" ")) {

                    tmp = new Edges();
                    tmp.in = new Index();
                    type = getType(s_or);

                    tmp.in.top = getTop(s_or, type);
                    if (type == 2)
                        tmp.in.level = getLevel(s_or, type);
                    else if (type == 3) {
                        tmp.in.level = getLevel(s_or, type);
                        tmp.in.basic = getBasic(s_or, type);
                    }
                    //Mark it as OR edge
                    tmp.OR = 1;
                    //add all sources in a list
                    temp.add(tmp);
                }
                //add that list to list of edges
                n.edges.add(temp);
            }

            //move up line by line j=length of current line
            i = j;
        }

        /*********************************************************************************************/
        //WEIGHTS
        i = input.indexOf("rsdg-weights") + 16;        //move up the index

        //if the useless-weights section is defined, then cut out that part from parsing
        if (input.indexOf("useless-weights") != -1)
            input = input.substring(0, input.indexOf("useless-weights"));

        input = input.substring(i, input.length());
        for (i = 0; i < input.length() - 1; i++) {

            ch1 = input.charAt(i);
            ch2 = input.charAt(i + 1);
            String w;        //cost
            String v;        //value
            if (ch1 == 'S' && ch2 == '_') {
                //Get the whole edge ex. S_2_1_1_4_1_1
                String n = input.substring(i, input.indexOf(" ", i));
                //Extract the weight
                w = input.substring(i + n.length() + 1, input.indexOf(" ", i + n.length() + 1));
                //Extract the value
                v = input.substring((input.indexOf(" ", i + n.length() + 1) + 1),
                        (input.indexOf(" ", (input.indexOf(" ", i + n.length() + 1) + 1))));
                if (v.indexOf(')') > 0)
                    v = v.substring(0, v.indexOf(')'));

                //Create temporary Edge and index
                Edges temp = new Edges();
                temp.in = new Index();

                //node weights
                type = getType(n);
                if (type == 3) {
                    temp.in.top = getTop(n, type);
                    temp.in.level = getLevel(n, type);
                    temp.in.basic = getBasic(n, type);

                    //get the node corresponding to the sink
                    Basic no = (Basic) graph.get(temp.in);
                    double c = Double.parseDouble(w);
                    if (v.length() != 0) {
                        double val = Double.parseDouble(v.trim());
                        no.value = val;
                    }

                    no.cost = c;
                }

                //edge weights
                else if (type == 6) {
                    //Split the string in two nodes ex. S_2_1_1_4_1_1 to S_2_1_1 and S_4_1_1

                    int k = 0;
                    int count = 0;

                    //get the 4th '_' which joins both
                    for (k = 0; k < n.length(); k++) {
                        if (n.charAt(k) == '_')
                            count++;
                        if (count == 4)
                            break;
                    }
                    //construct the sink
                    String n2 = "S" + n.substring(k, n.length());
                    //break the whole thing to get the source
                    String n1 = n.substring(0, k);

                    //source node
                    temp.in.top = getTop(n1, type);
                    temp.in.level = getLevel(n1, type);
                    temp.in.basic = getBasic(n1, type);
                    Node no = graph.get(temp.in);

                    //sink node
                    Edges si = new Edges();
                    si.in = new Index();
                    si.in.top = getTop(n2, type);
                    si.in.level = getLevel(n2, type);
                    si.in.basic = getBasic(n2, type);

                    for (int it = 0; it < no.edges.size(); it++) {
                        for (j = 0; j < no.edges.get(it).size(); j++) {
                            if (no.edges.get(it).get(j).in.top == si.in.top &&
                                    no.edges.get(it).get(j).in.level == si.in.level &&
                                    no.edges.get(it).get(j).in.basic == si.in.basic)
                                no.edges.get(it).get(j).cost = Double.parseDouble(w.substring(0, w.length() - 1));
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
        if (type == 1)        //ex. S_21
            return Integer.parseInt(node.substring(2, node.length()));

        else            // ex. S_21_2 or S_21_2_1
            return Integer.parseInt(node.substring(2, node.indexOf('_', 3)));
    }

    //extract the level of node
    public int getLevel(String node, int type) {

		/*level is between S_top_ and end of string (for top node)
    	or between 2nd and 3rd  '_' example: S_21_level_basic) (for other nodes)
    	Use type to determine node type*/
        if (type == 2)        //ex. S_21_4
            return Integer.parseInt(node.substring(node.indexOf('_', 2) + 1, node.length()));

        else            // ex. S_21_2_1
            return Integer.parseInt(node.substring(node.indexOf('_', 2) + 1, node.lastIndexOf('_')));
    }

    //extract the basic of node
    public int getBasic(String node, int type) {

		/*basic is the value after last '_'
    	type has to be 3 for this node*/
        return Integer.parseInt(node.substring(node.lastIndexOf('_') + 1, node.length()));
    }

    //Determine the type of node 1- Top 2 - Level 3 - Basic
    public int getType(String node) {
        //count number of '_' to determine node type
        int type = 0;
        for (int k = 0; k < node.length(); k++) {
            if (node.charAt(k) == '_')
                type++;
        }

        return type;
    }


    //Create the .lp file from the RSDG structure obtained
    @Override
    public void writeSchemeFile(String filename) {
        //create the lp file with the same name as that of the scheme file
        filename = filename.substring(0, filename.indexOf("."));
        filename = filename + ".lp";
        int i = 0;
        int basic = 0;
        String objective = "";
        String energy = "";
        String net_energy = "";
        String edge_int = "";

        try {
            FileWriter fileWriter = new FileWriter(filename);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            //OBJECTIVE
            bufferedWriter.write("Maximize");
            bufferedWriter.newLine();

            for (Index key : graph.keySet()) {
                if (key.level != 0 && key.basic != 0)
                    basic++;
            }


            //run through all nodes and print basic nodes
            for (Index key : graph.keySet()) {
                //write only the basic level nodes and their values
                if (key.level != 0 && key.basic != 0) {
                    i++;
                    objective = "";
                    energy = "";
                    Basic node = (Basic) graph.get(key);
                    //objective is to high highest value
                    //string objective stores the val and each node
                    objective = objective + "\t" + node.value +
                            " S_" + key.top + "_" + key.level + "_" + key.basic + "\n";

                    //this is the final energy(cost) constraint
                    energy = energy + "\t" + node.cost +
                            " S_" + key.top + "_" + key.level + "_" + key.basic + "\n";
                    // bufferedWriter.newLine();
                    //append the + after each node, except last
                    if (i != basic) {
                        objective = objective + "+ ";
                        energy = energy + "+ ";
                    }

                    bufferedWriter.write(objective);
                    net_energy += energy;

                    LinkedList<Edges> edgeList;
                    for (int j = 0; j < node.edges.size(); j++) {
                        edgeList = node.edges.get(j);
                        for (Edges edge : edgeList) {
                            objective = "";
                            energy = "";
                            if (edge.cost != 0) {
                                energy = "\t" + edge.cost + " " +
                                        "S_" + key.top + "_" + key.level + "_" + key.basic +
                                        "_" + edge.in.top + "_" + edge.in.level + "_" + edge.in.basic + "\n";
                                objective = "\t" + "0.0" + " " +
                                        "S_" + key.top + "_" + key.level + "_" + key.basic +
                                        "_" + edge.in.top + "_" + edge.in.level + "_" + edge.in.basic + "\n";
                                if (i != basic) {
                                    objective += "+ ";
                                    energy += "+";
                                }
                                bufferedWriter.write(objective);
                                net_energy += energy;
                            }
                        }
                    }
                }
            }

            bufferedWriter.newLine();

            //CONSTRAINTS
            int c = 1;                                //constraint number
            String constraint = "";
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
            for (Index key : graph.keySet()) {
                //top node
                if (key.basic == 0 && key.level == 0) {

                    //For each top node, store the basic and level nodes in 2 lists
                    LinkedList<Index> level_nodes = new LinkedList<>();
                    LinkedList<Index> basic_nodes = new LinkedList<>();

                    //run through the graph to find nodes within the service
                    for (Index index : graph.keySet()) {
                        constraint = "";
                        if (index.top == key.top) {        //belongs to same service
                            Index temp = new Index();

                            //level nodes
                            if (index.top != 0 && index.level != 0 && index.basic == 0) {
                                temp.top = index.top;
                                temp.level = index.level;
                                level_nodes.add(temp);
                            }

                            //basic nodes
                            else if (index.top != 0 && index.level != 0 && index.basic != 0) {
                                temp.top = index.top;
                                temp.level = index.level;
                                temp.basic = index.basic;
                                basic_nodes.add(temp);
                            }
                        }
                    }

                    //1. All basic nodes in a service - levels/implementations of a service =0
                    for (i = 0; i < level_nodes.size(); i++) {            //for each level, get the basic nodes in that level
                        constraint = "c" + c + ": ";
                        c++;
                        for (int j = 0; j < basic_nodes.size(); j++) {
                            //basic nodes of same level
                            if (basic_nodes.get(j).level == level_nodes.get(i).level) {
                                constraint = constraint + " S_" + basic_nodes.get(j).top +
                                        "_" + basic_nodes.get(j).level +
                                        "_" + basic_nodes.get(j).basic +
                                        " +";
                            }
                        }

                        //generate string
                        constraint = constraint.substring(0, constraint.lastIndexOf(' '));
                        constraint = constraint + " - S_" + level_nodes.get(i).top
                                + "_" + level_nodes.get(i).level + " = 0";

                        bufferedWriter.write(constraint);
                        bufferedWriter.newLine();

                    }

                    //2. All levels in a service - top node of service =0
                    constraint = "c" + c + ": ";
                    c++;
                    for (i = 0; i < level_nodes.size(); i++) {
                        constraint = constraint + " S_" + level_nodes.get(i).top +
                                "_" + level_nodes.get(i).level +
                                " +";
                    }

                    //generate string
                    constraint = constraint.substring(0, constraint.lastIndexOf(' '));
                    constraint = constraint + " - S_" + key.top + " = 0";

                    bufferedWriter.write(constraint);
                    bufferedWriter.newLine();

                    int e = 0;
                    //3. Sink - all weighted edges to it  = 0
                    //run through basic nodes
                    for (i = 0; i < basic_nodes.size(); i++) {
                        constraint = "c" + c + ": ";
                        //flag
                        e = 0;
                        Node n = graph.get(basic_nodes.get(i));
                        if (n.edges.size() != 0) {
                            constraint += " S_" + basic_nodes.get(i).top + "_" +
                                    basic_nodes.get(i).level + "_" +
                                    basic_nodes.get(i).basic + " - ";

                            for (int j = 0; j < n.edges.size(); j++) {
                                for (int k = 0; k < n.edges.get(j).size(); k++) {
                                    if (n.edges.get(j).get(k).cost != 0) {
                                        e = 1;
                                        constraint += " S_" + basic_nodes.get(i).top + "_" +
                                                basic_nodes.get(i).level + "_" +
                                                basic_nodes.get(i).basic + "_" +
                                                n.edges.get(j).get(k).in.top + "_" +
                                                n.edges.get(j).get(k).in.level + "_" +
                                                n.edges.get(j).get(k).in.basic + " -";
                                    }
                                }
                            }

                            if (e == 1) {
                                c++;
                                constraint = constraint.substring(0, constraint.lastIndexOf('-'));
                                constraint += " = 0";
                                bufferedWriter.write(constraint);
                                bufferedWriter.newLine();
                            }
                        }
                    }

                    //4. AND edges : Source - sink >=0
                    //Run through the basic nodes
                    for (i = 0; i < basic_nodes.size(); i++) {
                        //Get the basic node
                        Node n = graph.get(basic_nodes.get(i));
                        //run through the edges
                        for (int j = 0; j < n.edges.size(); j++) {
                            // size =1 means the list represents AND edge
                            if (n.edges.get(j).size() == 1) {
                                constraint = "c" + c + ": ";
                                constraint += " S_" + n.edges.get(j).get(0).in.top + "_"
                                        + n.edges.get(j).get(0).in.level + "_"
                                        + n.edges.get(j).get(0).in.basic + " - "
                                        + "S_" + basic_nodes.get(i).top + "_"
                                        + basic_nodes.get(i).level + "_"
                                        + basic_nodes.get(i).basic + " >= 0";
                                bufferedWriter.write(constraint);
                                bufferedWriter.newLine();
                                c++;

                            }

                            // 5. OR  edges : All sources - sink >=0
                            else {
                                //store group of OR edges in or_edges
                                LinkedList<Edges> or_edges = n.edges.get(j);
                                constraint = "c" + c + ": ";
                                c++;
                                // Sum of all OR edges
                                for (int k = 0; k < or_edges.size(); k++) {
                                    constraint += " S_" + or_edges.get(k).in.top + "_"
                                            + or_edges.get(k).in.level + "_"
                                            + or_edges.get(k).in.basic + " + ";
                                }

                                //Truncate extra '+' at the end
                                constraint = constraint.substring(0, constraint.lastIndexOf('+'));

                                constraint += " - " + "S_" + basic_nodes.get(i).top + "_"
                                        + basic_nodes.get(i).level + "_"
                                        + basic_nodes.get(i).basic + " >= 0";
                                bufferedWriter.write(constraint);
                                bufferedWriter.newLine();

                            }

                            // 6. Weighted Edge - Source <=0
                            for (int k = 0; k < n.edges.get(j).size(); k++) {
                                //if the edge is weighted
                                if (n.edges.get(j).get(k).cost != 0) {
                                    constraint = "c" + c + ": ";
                                    c++;
                                    String source = n.edges.get(j).get(k).in.top + "_"
                                            + n.edges.get(j).get(k).in.level + "_"
                                            + n.edges.get(j).get(k).in.basic;
                                    constraint += " S_" + basic_nodes.get(i).top + "_"
                                            + basic_nodes.get(i).level + "_"
                                            + basic_nodes.get(i).basic + "_"
                                            + source + " - " +
                                            "S_" + source +
                                            " <= 0";
                                    bufferedWriter.write(constraint);
                                    bufferedWriter.newLine();
                                }
                            }
                        }
                    }
                }
            }

            //7.overall constraint with energy
            constraint = "\nc" + c + ":  " + net_energy.trim() + "\n- energy = 0\n";
            bufferedWriter.write(constraint);
            c++;


            //Set energy constraint
            System.out.println("Enter budget");
            budget = sc.nextDouble();

            constraint = "\nc" + c + ": energy <= " + budget + "\n";
            bufferedWriter.write(constraint);

            //BOUNDS
            bufferedWriter.newLine();
            bufferedWriter.write("Bounds");
            bufferedWriter.newLine();
            String bounds = "";

            //print the nodes
            for (Index key : graph.keySet()) {
                bounds = "S_" + key.top;
                if (key.level != 0)
                    bounds = bounds + "_" + key.level;

                if (key.basic != 0)
                    bounds = bounds + "_" + key.basic;

                bounds = bounds + " <= 1";

                bufferedWriter.write(bounds);
                bufferedWriter.newLine();

                //print the weighted edges
                if (graph.get(key).edges.size() != 0) {
                    //if edges exist, check for weighted edges
                    Basic node = (Basic) graph.get(key);
                    LinkedList<Edges> edgeList;
                    for (int j = 0; j < node.edges.size(); j++) {
                        edgeList = node.edges.get(j);
                        for (Edges edge : edgeList) {
                            if (edge.cost != 0) {
                                bounds = "";
                                bounds = "S_" + key.top + "_" + key.level + "_" + key.basic +
                                        "_" + edge.in.top + "_" + edge.in.level + "_" + edge.in.basic +
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
            String intg = "";
            for (Index key : graph.keySet()) {
                intg = "S_" + key.top;
                if (key.level != 0)
                    intg = intg + "_" + key.level;

                if (key.basic != 0)
                    intg = intg + "_" + key.basic;

                bufferedWriter.write(intg);
                bufferedWriter.write(edge_int);
                intg = "";
                bufferedWriter.newLine();
                if (key.level == 0 || key.basic == 0)
                    continue;
                Basic node = (Basic) graph.get(key);
                if (node.edges.size() != 0) {
                    for (int j = 0; j < node.edges.size(); j++) {
                        LinkedList<Edges> edgeList;
                        edgeList = node.edges.get(j);
                        for (Edges edge : edgeList) {
                            if (edge.cost != 0) {
                                bufferedWriter.write("S_" + key.top + "_" + key.level + "_" + key.basic +
                                        "_" + edge.in.top + "_" + edge.in.level + "_" + edge.in.basic);
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
        } catch (IOException ex) {
            System.out.println(
                    "Error writing to file '"
                            + filename + "'");
            // Or we could just do this:
            // ex.printStackTrace();
        }
    }

    @Override
    public void parseXMLFile(String filecontent) {
        Log.d("RSDG", "preparing to parse the file");
        int i = 0, j = 0, k = 0;
        //Main list of services that will hold the whole structure
        graph_XML = new LinkedList<>();

        //Read xml using document builder
        try {
            //use document builder to parse the xml file
            DocumentBuilderFactory dbf =
                    DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Log.d("RSDG", "parsing");
            InputSource is = new InputSource(new StringReader(filecontent));
            Document dom = db.parse(is);
            Element doc = dom.getDocumentElement();

            //get list of all services in top
            NodeList top = doc.getElementsByTagName("service");
            Log.d("RSDG", "top nodes done");
            Log.d("RSDG", "Topnode:" + top.getLength());

            //run through all top nodes/ services
            for (i = 0; i < top.getLength(); i++) {
                //get each top node element
                Element t = (Element) top.item(i);
                //temporary top node
                XML.tNode ttemp = new tNode();

                //store service name
                ttemp.name = t.getElementsByTagName("servicename").item(0).getTextContent();

                //get the list of levels in that service
                NodeList level = t.getElementsByTagName("servicelayer");

                //if levels exist, initialize the list in data structure
                if (level.getLength() > 0)
                    ttemp.levelnodes = new LinkedList<>();

                //run through all levels in that service
                for (j = 0; j < level.getLength(); j++) {
                    //store each level element
                    Element l = (Element) level.item(j);
                    //temp level node
                    XML.lNode ltemp = new XML.lNode();

                    //get list of basic nodes in that level of that service
                    NodeList basic = l.getElementsByTagName("basicnode");

                    //Naming convention of level nodes will be Topnode_Levelnumber
                    ltemp.name = ttemp.name + "_" + (j + 1);

                    // if basic nodes exists in that level, initialize the list in DS
                    if (basic.getLength() > 0)
                        ltemp.basicnodes = new LinkedList<>();

                    //run through basic nodes
                    for (k = 0; k < basic.getLength(); k++) {

                        //temp basic node
                        XML.bNode btemp = new XML.bNode();
                        //get the xml element for each basic node
                        Element b = (Element) basic.item(k);

                        //store the basic node name
                        btemp.name = b.getElementsByTagName("nodename").item(0).getTextContent();

                        //store the basic node cost
                        btemp.cost = Double.parseDouble(b.getElementsByTagName("nodecost").item(0).getTextContent());

								/* This takes the mission value from xml for testing. The
								 * actual format of xml does not have a MV field in it.
								 * Call update mission value function instead
								 */
                        btemp.value = 0;

                        //get the AND edges in  basic node
                        NodeList and_edges = b.getElementsByTagName("and");

                        //get the OR edges in the basic node
                        NodeList or_edges = b.getElementsByTagName("or");

                        //instantiate a list of edges
                        btemp.Edges = new LinkedList<>();

                        //if AND edges exist add them to the list
                        if (and_edges.getLength() > 0) {
                            //run through the AND edges
                            for (int m = 0; m < and_edges.getLength(); m++) {
                                //For each and edge create a list (for AND, each list will have 1 element)
                                LinkedList<Edge> and = new LinkedList<>();
                                Element a = (Element) and_edges.item(m);
                                //create a temp edge
                                Edge temp = new Edge();
                                //get the name of source node ( just 1  AND edge )
                                temp.name = a.getElementsByTagName("name").item(0).getTextContent();
                                //get the edge weight
                                temp.weight = Double.parseDouble
                                        (a.getElementsByTagName("weight").item(0).getTextContent());

                                //increment the number of weighted edges found
                                if (temp.weight != 0)
                                    btemp.num_weighted_edges++;

                                //Add single node in the list
                                and.add(temp);
                                //add that list to list of edges of a basic node
                                btemp.Edges.add(and);
                            }
                        }

                        //Add or edges now
                        if (or_edges.getLength() > 0) {
                            //run through the sets of OR edges.
									/*Each or tag will have multiple name-weight pairs and
									 * each basic node can have multiple groups of OR edges
									 */
                            for (int m = 0; m < or_edges.getLength(); m++) {
                                //For each OR edge create a list (for OR, each list can have >1 element(s))
                                Element o = (Element) or_edges.item(m);

                                //Get the list of names and weights
                                NodeList names = o.getElementsByTagName("name");
                                NodeList weights = o.getElementsByTagName("weight");

                                //check that the name-weight pairs are consistent
                                if (names.getLength() != weights.getLength()) {
                                    System.out.println("Error in parsing:edge weight/name missing");
                                    System.exit(1);
                                }

                                LinkedList<Edge> or = new LinkedList<>();
                                //or edges exist
                                if (names.getLength() > 0) {
                                    //add all the or edges
                                    for (int p = 0; p < names.getLength(); p++) {
                                        Edge temp = new Edge();
                                        temp.name = names.item(p).getTextContent();
                                        temp.weight = Double.parseDouble(
                                                weights.item(p).getTextContent());

                                        //increment the number of weighted edges
                                        if (temp.weight != 0)
                                            btemp.num_weighted_edges++;

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

    @Override
    public void writeXMLFile(String filename) throws IOException {
        //create the lp file with the same name as that of the xml file
        filename = filename + ".lp";
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Log.d("XMLwrite", "can write to dir");
        }
        //File output = Environment.getExternalStorageDirectory();
        File output = Environment.getExternalStorageDirectory();
        String sdcardPath = output.getPath();
        Log.d("XMLwriter", "path=" + sdcardPath);
        File file = new File(sdcardPath + "/" + filename);
        Log.d("file dir", "=" + file.getAbsolutePath());
        file.createNewFile();
        int i = 0;
        String objective = "";
        String energy = "";

        FileWriter fileWriter = new FileWriter(file);
        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

        //OBJECTIVE
        bufferedWriter.write("Maximize");
        bufferedWriter.newLine();

        for (i = 0; i < graph_XML.size(); i++) {
            for (int j = 0; j < graph_XML.get(i).levelnodes.size(); j++) {
                for (int k = 0; k < graph_XML.get(i).levelnodes.get(j).basicnodes.size(); k++) {
                    XML.bNode temp = graph_XML.get(i).levelnodes.get(j).basicnodes.get(k);
                    objective += "\t" + temp.value + " " + temp.name + "\n+";
                    energy += "\t" + temp.cost + " " + temp.name + "\n+";
                }
            }
        }

        objective = objective.substring(0, objective.lastIndexOf('+'));
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

        int c = 1;
        String constraint = "";

        bufferedWriter.write("sys = 1\n");
        bufferedWriter.write("comm = 1\n");
        bufferedWriter.write("localization = 1\n");
        bufferedWriter.write("info = 1\n");
        bufferedWriter.write("screen = 1\n");
        //1. All levels in a service - top node of service =0
        for (i = 0; i < graph_XML.size(); i++) {
            constraint = "c" + c + ": ";
            for (int j = 0; j < graph_XML.get(i).levelnodes.size(); j++) {
                constraint += graph_XML.get(i).levelnodes.get(j).name + " + ";
            }
            constraint = constraint.substring(0, constraint.lastIndexOf('+'));
            constraint += "- " + graph_XML.get(i).name + " = 0";
            bufferedWriter.write(constraint);
            bufferedWriter.newLine();
            c++;
        }

        bufferedWriter.newLine();
        //2. All basic nodes in a service - levels/implementations of a service =0
        for (i = 0; i < graph_XML.size(); i++) {
            for (int j = 0; j < graph_XML.get(i).levelnodes.size(); j++) {
                constraint = "c" + c + ": ";
                for (int k = 0; k < graph_XML.get(i).levelnodes.get(j).basicnodes.size(); k++) {
                    constraint += graph_XML.get(i).levelnodes.get(j).basicnodes.get(k).name + " + ";
                }
                constraint = constraint.substring(0, constraint.lastIndexOf('+'));
                constraint += "- " + graph_XML.get(i).levelnodes.get(j).name + " = 0";
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
        String constraint4 = "";
        //run through the graph
        for (i = 0; i < graph_XML.size(); i++) {
            for (int j = 0; j < graph_XML.get(i).levelnodes.size(); j++) {
                for (int k = 0; k < graph_XML.get(i).levelnodes.get(j).basicnodes.size(); k++) {
                    //get the basic node
                    XML.bNode temp = graph_XML.get(i).levelnodes.get(j).basicnodes.get(k);
                    constraint4 = "";
                    //run through the edges
                    for (int l = 0; l < temp.Edges.size(); l++) {
                        for (int m = 0; m < temp.Edges.get(l).size(); m++) {
                            //do this only for weighted edges
                            if (temp.Edges.get(l).get(m).weight != 0) {

                                //Sink - all weighted edges = 0
                                constraint4 += temp.name + "$" + temp.Edges.get(l).get(m).name
                                        + " - ";
                                //Weighted Edge - Source <=0
                                bufferedWriter.write("c" + c + ": " +
                                        temp.name + "$" + temp.Edges.get(l).get(m).name +
                                        " - " + temp.Edges.get(l).get(m).name + " <= 0");
                                bufferedWriter.newLine();
                                c++;
                            }

                            //AND Edges: Source - Sink >= 0
                            if (temp.Edges.get(l).size() == 1) {
                                bufferedWriter.write("c" + c + ": " + temp.Edges.get(l).get(0).name
                                        + " - " + temp.name + " >= 0");
                                c++;
                                bufferedWriter.newLine();
                            }
                        }

                        //OR  Edges: All Sources - Sink >=0
                        if (temp.Edges.get(l).size() > 1) {
                            String constraint6 = "";
                            for (int m = 0; m < temp.Edges.get(l).size(); m++) {
                                constraint6 +=
                                        temp.Edges.get(l).get(m).name + " + ";
                            }
                            constraint6 = constraint6.substring(0, constraint6.lastIndexOf('+'));
                            constraint6 += " - " + temp.name + " >= 0";
                            constraint6 = "c" + c + ": " + constraint6;
                            c++;
                            bufferedWriter.write(constraint6);
                            bufferedWriter.newLine();
                        }
                    }
                    if (constraint4.indexOf('-') > 0) {
                        constraint4 = constraint4.substring(0, constraint4.lastIndexOf('-'));
                        constraint4 = "c" + c + ": " + temp.name + " - " + constraint4 + " = 0";
                        c++;
                        bufferedWriter.write(constraint4);
                        bufferedWriter.newLine();
                    }
                }
            }
        }


        //7. Overall Energy constraint
        bufferedWriter.newLine();
        bufferedWriter.write("c" + c + ": " + energy.trim().substring(0, energy.trim().lastIndexOf('+'))
                + "- energy" + " = 0");
        bufferedWriter.newLine();
        c++;
        bufferedWriter.newLine();
        bufferedWriter.write("c" + c + ": " + "energy <= " + budget);
        bufferedWriter.newLine();

        //Bounds
        bufferedWriter.newLine();
        bufferedWriter.write("Bounds");
        bufferedWriter.newLine();
        String integers = "";
        for (i = 0; i < graph_XML.size(); i++) {
            bufferedWriter.write(graph_XML.get(i).name + " <= 1");
            integers += graph_XML.get(i).name + "\n";
            bufferedWriter.newLine();
            for (int j = 0; j < graph_XML.get(i).levelnodes.size(); j++) {
                bufferedWriter.write(graph_XML.get(i).levelnodes.get(j).name + " <= 1");
                integers += graph_XML.get(i).levelnodes.get(j).name + "\n";
                bufferedWriter.newLine();
                for (int k = 0; k < graph_XML.get(i).levelnodes.get(j).basicnodes.size(); k++) {
                    XML.bNode temp = graph_XML.get(i).levelnodes.get(j).basicnodes.get(k);
                    integers += temp.name + "\n";
                    bufferedWriter.write(temp.name + " <= 1");
                    bufferedWriter.newLine();
                    for (int l = 0; l < temp.Edges.size(); l++) {
                        for (int m = 0; m < temp.Edges.get(l).size(); m++) {
                            if (temp.Edges.get(l).get(m).weight != 0) {
                                integers += temp.name + "$" +
                                        temp.Edges.get(l).get(m).name + "\n";
                                bufferedWriter.write(temp.name + "$" +
                                        temp.Edges.get(l).get(m).name + " <= 1");
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

    public void updateMissionValue(String serviceName, int value, boolean exp, int xml) {

        //flag if service is found
        int found = 0;

        //TODO: Implement scheme part
        if (xml == 1) {
            //store the service if found
            tNode service = null;
            //run through the service names
            for (int i = 0; i < graph_XML.size(); i++) {
                //found
                if ((graph_XML.get(i).name.compareToIgnoreCase(serviceName)) == 0) {
                    //store the service
                    service = graph_XML.get(i);
                    found = 1;
                    break;
                }
            }

            //service doesn't exist
            if (found == 0) {
                System.out.println("Service " + serviceName + "not found");
            }

            //service found
            else {
                //get all the levels in the service
                LinkedList<lNode> levels = service.levelnodes;
				/*
				 * We need to set mission values to all basic nodes in each level
				 */
                for (int i = 0; i < levels.size(); i++) {
                    double currentV;
                    //now, depending on exponential flag, change the value of input mission value
                    //linear decrease
                    if (exp == false) {
                        currentV = (double) value / levels.size() * (levels.size() - i);
                    }
                    currentV = (double) value / levels.size() * (levels.size() - i);
                    //TODO: Exponential decrease

                    //set the same value to all basic nodes in a level
                    for (int j = 0; j < levels.get(i).basicnodes.size(); j++) {
                        levels.get(i).basicnodes.get(j).value = currentV;
                    }
                }
            }
        }

        //Scheme
        else if (xml == 0) {

            Top service = null;
            //check if it's a top node
            if (serviceName.length() != 3) {
                System.out.println("Node is not a service");
                return;
            }
            //store the service if found
            Index in = new Index();
            in.basic = in.level = 0;
            in.top = getTop(serviceName, 1);
            service = (Top) graph.get(in);

            if (service == null) {
                System.out.println("Invalid node");
                return;
            }

            //get the number of levels in that service
            int num_of_levels = 0;
            for (Index index : graph.keySet()) {

                //same service, but level node
                if (index.top == in.top && index.level > 0 && index.basic == 0) {
                    if (index.level > num_of_levels)
                        num_of_levels = index.level;
                }
            }
            //now set the value to the basic nodes in that level

            for (Index index : graph.keySet()) {

                //same service and it's a basic node
                if (index.top == in.top && index.basic > 0) {
                    Basic temp = (Basic) graph.get(index);
                    //Linear
                    if (exp == false)
                        temp.value = value / num_of_levels * (num_of_levels - index.level + 1);
                    //TODO: Exponential
                }
            }


        }
    }

    public void updateCost(String basic, int cost, int xml) {

        //flag to check basic node exists
        int found = 0;

        //Xml part
        if (xml == 1) {
            //store the basic node if found
            bNode temp = null;
            for (int i = 0; i < graph_XML.size(); i++) {
                for (int j = 0; j < graph_XML.get(i).levelnodes.size(); j++) {
                    for (int k = 0; k < graph_XML.get(i).levelnodes.get(j).basicnodes.size(); k++) {
                        if (graph_XML.get(i).levelnodes.get(j).basicnodes.get(k).name.compareTo(basic) == 0) {
                            found = 1;
                            temp = graph_XML.get(i).levelnodes.get(j).basicnodes.get(k);
                            break;
                        }
                    }
                }
            }

            if (found == 0) {
                System.out.println("basic node not found");
            } else {
                temp.cost = cost;
            }

        }

        //Scheme
        else if (xml == 0) {
            //check validity
            if (basic.length() != 7) {
                System.out.println("Not a basic node");
                return;
            }
            //extract the top,level,basic for the basic node (type =2)
            Index index = new Index();
            index.top = getTop(basic, 3);
            index.level = getLevel(basic, 3);
            index.basic = getLevel(basic, 3);


            Basic temp = (Basic) graph.get(index);
            //node not found
            if (temp == null) {
                System.out.println("basic node not found");
                return;
            }

            //if found, update cost
            temp.cost = cost;
            graph.put(index, temp);
        }

    }

    /*
     * This heuristic selects the basic nodes such that nodes with the highest
     * mission value is chosen first. It's dependencies are selected such that
     * the energy required is the lowest.
     */
    public String highNodeLowDep() {
        //check if RSDG exists
        if (graph_XML == null) {
            System.out.println("No input given");
            return "";
        }

        int size = 0;                        //total nodes in the RSDG
        int size_edges = 0;                //total weighted edges in RSDG
        int i = 0, j = 0, k = 0;                    //loop counters
        int net_energy = 0, net_mission = 0;  //total energy and mission value of the heuristic
        int top = 0;                        //number of services/ top nodes

        //list for all basic nodes
        LinkedList<bNode> basic = new LinkedList<>();

        //calculate the total number of nodes and weighted edges
        //Also add the basic nodes found while traversing in the list of basic nodes
        for (i = 0; i < graph_XML.size(); i++) {
            top++;        //store number of services
            size++;        //store total number of nodes
            for (j = 0; j < graph_XML.get(i).levelnodes.size(); j++) {
                size++;
                for (k = 0; k < graph_XML.get(i).levelnodes.get(j).basicnodes.size(); k++) {
                    size++;
                    bNode temp = graph_XML.get(i).levelnodes.get(j).basicnodes.get(k);
                    basic.add(temp);

                    //add the number of weighted edges to calculate total weighted edges
                    size_edges += temp.num_weighted_edges;
                }
            }
        }

        //sort the basic node list in desc. order of values
        Collections.sort(basic, Collections.reverseOrder());

        //This will be the final result array which will display
        //all the nodes (service,level and basic nodes) that are enabled or disabled
        nodeList[] result = new nodeList[size];

        //Similarly, this array will store the final weighted edges
        nodeList[] edge_result = new nodeList[size_edges];

		/* a temp array will be used to mark nodes as unselected if
		 * a node from the same service is chosen. This ensures no two
		 * nodes are selected from the same service.
		 */
        int[] unselect = new int[top];

        //initialize the result array
        for (i = 0; i < result.length; i++)
            result[i] = new nodeList();

        //initialize the weighted edge result array
        for (i = 0; i < edge_result.length; i++) {
            edge_result[i] = new nodeList();
        }

        //array which will be our final result array to be displayed
        //initialize the name of all the nodes in the array
        int ctr = 0;
        int ctr_edge = 0;
        for (i = 0; i < graph_XML.size(); i++) {
            result[ctr++].name = graph_XML.get(i).name;
            for (j = 0; j < graph_XML.get(i).levelnodes.size(); j++) {
                result[ctr++].name = graph_XML.get(i).levelnodes.get(j).name;
                for (k = 0; k < graph_XML.get(i).levelnodes.get(j).basicnodes.size(); k++) {
                    bNode temp = graph_XML.get(i).levelnodes.get(j).basicnodes.get(k);
                    result[ctr++].name = temp.name;
                    for (int l = 0; l < temp.Edges.size(); l++) {
                        for (int m = 0; m < temp.Edges.get(l).size(); m++) {
                            if (temp.Edges.get(l).get(m).weight != 0)
                                edge_result[ctr_edge++].name = temp.name + "$" + temp.Edges.get(l).get(m).name;
                        }
                    }
                }
            }
        }

		/*Now calculate the closure of deps for each selected basic node.
		 *make an array of nodeList( of type node name, selected flag)
		 *that will mark all the basic nodes turned on due to dependencies.
		 *This array will store the supplementary result for one basic node
		 *If node + its deps satisfy energy constraint, we mark them selected
		 *in the final result array named result
		 *Also mark the edges that are selected while calculating the closure
		 */
        nodeList[] sup_result;
        nodeList[] sup_edge_result;
		/*
		 * Similar to supplementary result, each node will have supplementary
		 * unselected array which will hold the services that we can't select.
		 */

        int[] sup_unselect;

        //For each basic node, calculate the dependencies and the cost needed
        for (i = 0; i < basic.size(); i++) {
            int energy = 0;
            int mv = 0;
            //get basic node
            bNode temp = basic.get(i);
            //check dependencies for nodes with non-zero mission value only

            if (temp.value == 0)
                continue;

            //this array will hold the closure of dependent nodes
            sup_result = new nodeList[basic.size()];

            //this array will hold the edges that have been selected
            sup_edge_result = new nodeList[size_edges];

            //initialize the supplementary result array
            for (j = 0; j < basic.size(); j++) {
                sup_result[j] = new nodeList();
                sup_result[j].name = basic.get(j).name;
                sup_result[j].selected = 0;
            }

            //initialize the supplementary edge result array
            for (j = 0; j < sup_edge_result.length; j++) {
                sup_edge_result[j] = new nodeList();
                sup_edge_result[j].name = edge_result[j].name;
            }

            if (i >= unselect.length || unselect[i] == 1)
                continue;

			/*this array will hold the services that can't be selected
			 *since one of the nodes is selected from that service
			 */
            sup_unselect = new int[top + 1];

            //mark that node as selected and then compute it's closure
            sup_result[i].selected = 1;

            //once a node is selected, mark that service not selectable
            //get the service the basic node belongs to and mark it not selectable
            int topnode = getTopNode(sup_result[i].name);
            sup_unselect[topnode] = 1;

            //find the closure
            int fail = findSupplementaryCost(sup_result, basic, sup_unselect, result, sup_edge_result);

            if (fail == 1)
                continue;

            //compute the energy required for selecting a node and it's deps
            //if it's under the total cost mark them selected

            for (j = 0; j < sup_result.length; j++) {
                if (sup_result[j].selected == 1) {
                    //   System.out.print(sup_result[j].name+ ",");
                    energy += getBasicNode(sup_result[j].name, basic).cost;
                    mv += getBasicNode(sup_result[j].name, basic).value;
                }
            }

            for (j = 0; j < sup_edge_result.length; j++) {
                if (sup_edge_result[j].selected == 1) {
                    energy += getEdgeWeight(sup_edge_result[j].name, basic);
                }
            }

            if (energy + net_energy > budget)
                continue;

            //if it's in the budget, add it to the total/net
            net_energy += energy;
            net_mission += mv;

			/* Set the newly selected nodes as chosen in the result
			 * At the same time, mark the service as chosen in the unselect array.
			 * we can later use unselect array to avoid choosing nodes from the same service
			 * (but allow if same node is being chosen even though that service is marked unselected)
			 */
            for (j = 0; j < sup_result.length; j++) {
                if (sup_result[j].selected == 1) {
                    topnode = getTopNode(sup_result[j].name);
                    if (unselect[topnode] != 1) {
                        setSelected(sup_result[j].name, result);
                        unselect[topnode] = 1;
                    }
                }
            }

            for (j = 0; j < sup_edge_result.length; j++) {
                if (sup_edge_result[j].selected == 1) {
                    edge_result[j].selected = 1;
                }
            }
        }

        String res = "Mission: " + net_mission + "\n";

        for (i = 0; i < result.length; i++)
            res += result[i].name + " " + result[i].selected + "\n";

        for (i = 0; i < edge_result.length; i++)
            res += edge_result[i].name + " " + edge_result[i].selected + "\n";

        res += "Energy: " + net_energy + "\n";

        return res;
    }



	/*
	 * Get the edge weight from the edge name
	 */

    public double getEdgeWeight(String name, LinkedList<bNode> basic) {

        //extract the sink from edge name
        bNode sink = getBasicNode(name.substring(0, name.indexOf('$')), basic);

        for (int i = 0; i < sink.Edges.size(); i++) {
            for (int j = 0; j < sink.Edges.get(i).size(); j++) {
                if (sink.Edges.get(i).get(j).name.compareTo(name.substring(name.indexOf('$') + 1, name.length())) == 0)
                    return sink.Edges.get(i).get(j).weight;
            }
        }
        return 0;
    }

    //overloaded function to get edge weight, given sink and source name
    public double getEdgeWeight(String sink, String source) {

        int i, j, k;
        bNode sinknode, temp;
        sinknode = temp = null;

        for (i = 0; i < graph_XML.size(); i++) {
            for (j = 0; j < graph_XML.get(i).levelnodes.size(); j++) {
                for (k = 0; k < graph_XML.get(i).levelnodes.get(j).basicnodes.size(); k++) {
                    temp = graph_XML.get(i).levelnodes.get(j).basicnodes.get(k);
                    if (temp.name.compareTo(sink) == 0) {
                        sinknode = temp;
                        break;
                    }
                }
            }
        }

        if (sinknode != null) {
            for (i = 0; i < sinknode.Edges.size(); i++) {
                for (j = 0; j < sinknode.Edges.get(i).size(); j++) {
                    if (sinknode.Edges.get(i).get(j).name.compareTo(source) == 0) {
                        return sinknode.Edges.get(i).get(j).weight;
                    }
                }
            }
        }

        return 0;
    }

    //Function to set edge weight, given sink and source name
    public void setEdgeWeight(String sink, String source, double weight) {

        int i, j, k;
        bNode sinknode, temp;
        sinknode = temp = null;

        for (i = 0; i < graph_XML.size(); i++) {
            for (j = 0; j < graph_XML.get(i).levelnodes.size(); j++) {
                for (k = 0; k < graph_XML.get(i).levelnodes.get(j).basicnodes.size(); k++) {
                    temp = graph_XML.get(i).levelnodes.get(j).basicnodes.get(k);
                    if (temp.name.compareTo(sink) == 0) {
                        sinknode = temp;
                        break;
                    }
                }
            }
        }

        if (sinknode != null) {
            for (i = 0; i < sinknode.Edges.size(); i++) {
                for (j = 0; j < sinknode.Edges.get(i).size(); j++) {
                    if (sinknode.Edges.get(i).get(j).name.compareTo(source) == 0) {
                        sinknode.Edges.get(i).get(j).weight = weight;
                    }
                }
            }
        }
    }

	/*
	 * If a node is selected, we can't select other nodes in that service
	 * Hence, mark those nodes as unselected.
	 */

    public int getTopNode(String name) {
        int i, j, k;
        int t = 0;

        //get the service the basic node belongs in
        out:
        for (i = 0; i < graph_XML.size(); i++) {
            //record the top node position. (top go from 1 to number of top nodes
            t = i + 1;
            for (j = 0; j < graph_XML.get(i).levelnodes.size(); j++) {
                for (k = 0; k < graph_XML.get(i).levelnodes.get(j).basicnodes.size(); k++) {
                    //break at the point where you find the basic node
                    if (graph_XML.get(i).levelnodes.get(j).basicnodes.get(k).name.compareTo(name) == 0) {
                        //when the basic node is found and top recorded, break out of the outer loop
                        break out;
                    }
                }
            }
        }
        return t;
    }

    /* After computing the dependencies, if they satisfy energy constraint, mark
     * them as selected.
     */
    public void setSelected(String name, nodeList[] result) {

        int i, j, k;
        int t, l, b = 0;
        //run through the graph
        for (i = 0; i < graph_XML.size(); i++) {
            //store the top node
            t = b;
            b++;
            for (j = 0; j < graph_XML.get(i).levelnodes.size(); j++) {
                //store the level node
                l = b;
                b++;
                for (k = 0; k < graph_XML.get(i).levelnodes.get(j).basicnodes.size(); k++) {
                    //once the basic node is found that is to be marked selected, mark basic, level and top selected
                    if (graph_XML.get(i).levelnodes.get(j).basicnodes.get(k).name.compareTo(name) == 0) {
                        result[t].selected = 1;
                        result[l].selected = 1;
                        result[b].selected = 1;
                    }
                    b++;
                }
            }
        }
    }

    //Function to find the dependencies of a node selected
    public int findSupplementaryCost(nodeList[] sup, LinkedList<bNode> basic,
                                     int[] sup_unselect, nodeList[] result,
                                     nodeList[] sup_edge_result) {

		/*
		 * sup - list of supplementary nodes (nodes selected as dependencies on
		 * the current considered node)
		 * basic - list of all basic nodes in the graph
		 * sup_unselect - the services that are marked selected while computing closure
		 * result - the final array which stores the final selected nodes
		 * sup_edge_result - supplementary edges selected while computing the closure
		 *
		 */

        int i, j, k;
        int flag = 0;        //use the flag to determine if closure is complete
        int topnode = 0;       //store the service a basic node belongs in

        //find the closure until no dependency can be found.

        //run through all basic nodes to compute the closure
        for (i = 0; i < sup.length; i++) {
            flag = 0;

            //if node is not selected, don't check it's dependencies
            if (sup[i].selected != 1)
                continue;

            //process for a selected node

            //get the basic node
            bNode temp = getBasicNode(sup[i].name, basic);


			/* run through the edges of the basic node
			 * If and edges exist, mark the source as selected
			 * If or edges exists, mark the source with lowest cost as selected
			 */
            for (j = 0; j < temp.Edges.size(); j++) {
                LinkedList<Edge> edge = temp.Edges.get(j);
                //AND edge
                if (edge.size() == 1) {

					/* For AND edge, if the source lies in the service that's
					 * already selected, then we can't select it. Since we can't
					 * select it, we can't choose node that we are calculating the deps for!
					 */
                    topnode = getTopNode(edge.get(0).name);

					/*
					 * However, if the same basic node is to be selected, we need to select
					 * it even if the service is marked unselected.
					 * Check if the node was already selected. If yes, it's a valid selection
					 */

                    int prev_selected = checkSelected(edge.get(0).name, result);

                    //if a AND dependency fails, return 1 as a failure status
                    if (sup_unselect[topnode] == 1 && prev_selected != 1)
                        return 1;

                    //if we can select the node, go ahead..
                    flag = 0;
                    //get the position in the supplementary array which we need to mark as selected
                    int position = getPosition(edge.get(0).name, sup);

                    if (position == -1)
                        System.out.println("ERROR");

                    else {
                        //select that node
                        sup[position].selected = 1;

                        //select that edge as well
                        for (int ctr = 0; ctr < sup_edge_result.length; ctr++)
                            if (sup_edge_result[ctr].name.compareTo(temp.name + "$" + edge.get(0).name) == 0)
                                sup_edge_result[ctr].selected = 1;

                        //move back the array if any previous node is selected
                        if (position < i)
                            i = position;
                    }
                }

                //OR Edge having more
                else if (edge.size() > 1) {
                    //get the sources sorted in ascending order of their cost
                    LinkedList<bNode> sources;
                    //sort the sources and store it in sources list
                    sources = sortSourcesbyCost(edge, basic);

                    //run through the sources and select the lowest node
                    for (k = 0; k < sources.size(); k++) {
                        topnode = getTopNode(sources.get(i).name);
                        int prev_selected = checkSelected(sources.get(i).name, result);
                        //if service is not selected earlier or service is selected but node is repeated
                        if (sup_unselect[topnode] != 1 || prev_selected == 1) {
                            int position = getPosition(sources.get(i).name, sup);

                            //select the node
                            sup[position].selected = 1;

                            //select that edge as well
                            for (int ctr = 0; ctr < sup_edge_result.length; ctr++)
                                if (sup_edge_result[ctr].name.compareTo(temp.name + "$" + edge.get(0).name) == 0)
                                    sup_edge_result[ctr].selected = 1;

							/*if the node selected is at a position lower than i, move
							 i back to position to compute it's closure*/
                            if (position < i)
                                i = position;

                            //atleast one node is added then set flag = 0
                            flag = 0;
                            break;
                        }
                    }
                }

                //if nothing (no AND or OR edges) exist, control will come here and set flag to 1
                else
                    flag = 1;

                //if flag is 1, it means closure is done, there's nothing more to add
                if (flag == 1)
                    break;
            }
        }

        return 0;
    }

    /*
     * A node can be a sink to 2 or more nodes. But while selecting the source
     * the sink is selected as dependency and the whole service is marked selected
     * However, if another node has the same sink, it won't allow to select it since
     * service is already marked selected. Hence, check if node is previously selected
     * or not
     */
    public int checkSelected(String name, nodeList[] result) {

        int i = 0;

        //run through the result array to check if the node is already selected
        for (i = 0; i < result.length; i++) {

            if (result[i].name.compareTo(name) == 0) {
                if (result[i].selected == 1)
                    return 1;
                else
                    return 0;
            }
        }

        return 0;
    }

    /*
     * The Heuristic has a choice of selecting one of the edge sources from a
     * group of OR edges. Sort all the sources a sink has and return
     * the sorted list
     */
    public LinkedList<bNode> sortSourcesbyCost(LinkedList<Edge> edge, LinkedList<bNode> list) {

        //store the sources in a list
        LinkedList<bNode> sources = new LinkedList<>();

        //store the source as a string in src
        String src = "";

        //flag to denote if source is added
        int flag;

        //run through the sources
        for (int i = 0; i < edge.size(); i++) {
            flag = 0;
            src = edge.get(i).name;
            bNode temp = getBasicNode(src, list);

            //empty list : add element to start
            if (sources.size() == 0) {
                sources.add(temp);
            }

            //iterate through the list to find where to insert the element
            else {
                for (int j = 0; j < sources.size(); j++) {
                    //if temp cost is less than current element, insert before that element
                    if (temp.cost < sources.get(j).cost) {
                        sources.add(j, temp);
                        //break once added
                        flag = 1;
                        break;
                    }
                }
            }

            //if element is still not added, add at the end
            if (flag == 0)
                sources.addLast(temp);
        }

        return sources;
    }

    //gets a basic node from the list of basic nodes
    public bNode getBasicNode(String name, LinkedList<bNode> list) {
        int i = 0;
        for (i = 0; i < list.size(); i++) {
            if (list.get(i).name.compareTo(name) == 0)
                return list.get(i);
        }

        return null;
    }

    //get the position of a node from the list of all nodes
    public int getPosition(String name, nodeList[] list) {

        for (int i = 0; i < list.length; i++) {
            if (list[i].name.compareTo(name) == 0) {
                return i;
            }
        }
        return -1;
    }

	/*The result of the heuristic is a string with net objective, energy
	 * and a list of all nodes - selected and unselected.
	 * This function parses that result to give a list of the nodes that are selected,
	 * excluding the unselected ones and also the energy and objective part
	 */

    public LinkedList<String> getSelectedNodes(String result) {

        //linked list storing the selected nodes
        LinkedList<String> res = new LinkedList<>();
        //temp strings
        String node = "";
        String selected = "";
        int i = 0;

        //append a \n to the string to extract individual nodes
        result = result + "\n";

        //go through the result string
        for (i = 0; i < result.length(); i++) {
            //extract the string from \n to \n and store in node
            if (result.charAt(i) == '\n') {
                //node variable will hold the node name and selected flag (eg. S_1_2_3 0)
                node = result.substring(i, result.indexOf('\n', i + 1));

				/*
				 * The result also has weighted edges which we do not want.
				 * Hence, only parse the node string if $ isn't found in it; $ being
				 * part of an edge and not a node.
				 * The if condition skips edges and energy part
				 */
                if (node.indexOf('$') < 0 && node.indexOf(' ') > 0) {

                    //extract selected part
                    selected = node.substring(node.indexOf(' '), node.length());
                    //extract node part
                    node = node.substring(0, node.indexOf(' '));

                    //if the node is selected, add it to the list
                    if (selected.trim().compareTo("1") == 0)
                        res.add(node.trim());
                }

                //jump from line to line (\n to \n)
                i = i + node.length();
            }
        }

        return res;
    }
}

