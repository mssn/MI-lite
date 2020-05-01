package edu.purdue.cs.mssn.mi_lite;

public class DmCollector {
    static {
        System.loadLibrary("dmcollector");
    }

    public static native byte[] generateCustomPkt(String message);
}
