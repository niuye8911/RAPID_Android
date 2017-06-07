package com.example.liuliu.rsdglib;

import java.util.LinkedList;

public interface Scheme {

    public class Node {
        public LinkedList<LinkedList<Edges>> edges = null;
    }

    public class Basic extends Node {
        public double value = 0;
        public double cost = 0;
    }

    public class Level extends Node {
    }

    public class Top extends Node {
        String serviceName = "";
    }

    public class Index {
        protected int top;
        protected int level;
        protected int basic;

        public Index() {
            top = level = basic = 0;
        }

        @Override
        public boolean equals(Object obj) {
            Index in = (Index) obj;
            if (this.top == in.top && this.level == in.level && this.basic == in.basic)
                return true;
            else
                return false;
        }

        @Override
        public int hashCode() {
            int hashcode;
            hashcode = this.top * 100 + this.level * 10 + this.basic * 1;
            return hashcode;
        }
    }

    public class Edges {

        int OR;   //OR or AND edges
        Index in;  //Source of the edge
        double value;  //Mission Value
        double cost; //Cost      
    }

    public void parseSchemeList(String input);

    public String readSchemeFile(String filename);

    public void writeSchemeFile(String filename);


}