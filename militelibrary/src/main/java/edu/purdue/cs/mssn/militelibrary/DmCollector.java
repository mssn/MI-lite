package edu.purdue.cs.mssn.militelibrary;

public class DmCollector {
    static {
        System.loadLibrary("dmcollector");
    }

    public static native byte[] generateCustomPkt(String message);
}