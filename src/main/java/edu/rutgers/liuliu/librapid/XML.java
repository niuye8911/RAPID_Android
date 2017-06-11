package edu.rutgers.liuliu.librapid;/**
 * Created by liuliu on 4/19/16.
 */

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

public interface XML {

	/*Use a hierarchy of lists as the data structure
     * The graph will have a list of top nodes which will hold
	 * the number of levels, the service name and list of level nodes
	 * The level nodes will hold a list of basic nodes and
	 * the basic nodes will have a name and cost and list of sources
	 */

    class Edge {
        String name;
        double weight;
    }

    class bNode implements Comparable<bNode> {
        String name;
        double cost;    //weight
        double value;    //value
        LinkedList<LinkedList<Edge>> Edges;
        int num_weighted_edges;        //hold the number of weighted edges

        @Override
		/*Override the compareTo function for sorting the linkedlist
		 *on the basis of mission values.
		 */
        public int compareTo(bNode temp) {
            double comparevalue = temp.value;
            if (this.value > comparevalue) {
                return 1;
            } else if (this.value == comparevalue) {
                return 0;
            } else {
                return -1;
            }
        }
    }

    ;

    class lNode {
        LinkedList<bNode> basicnodes;
        String name;
    }

    ;

    class tNode {
        LinkedList<lNode> levelnodes;
        String name;
    }

    ;


    public void parseXMLFile(String filename);

    public void writeXMLFile(String filename) throws IOException;
}
