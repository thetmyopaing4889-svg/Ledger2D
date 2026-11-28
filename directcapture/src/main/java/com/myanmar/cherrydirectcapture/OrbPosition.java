package com.myanmar.cherrydirectcapture;
final class OrbPosition {
    static int clamp(int value,int extent,int size){return Math.max(0,Math.min(value,Math.max(0,extent-size)));}
    private OrbPosition(){}
}
