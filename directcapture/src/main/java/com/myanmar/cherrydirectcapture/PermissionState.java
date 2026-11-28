package com.myanmar.cherrydirectcapture;
/** Separate the system grant from the in-process connection. No settings writes. */
final class PermissionState {
    enum Status { DISABLED, CONNECTING, READY, UNKNOWN }
    static boolean containsComponent(String list,String pkg,String cls){
        if(list==null||pkg==null||cls==null)return false;
        for(String item:list.split(":")){
            int slash=item.indexOf('/');
            if(slash<1||slash!=item.lastIndexOf('/'))continue;
            String owner=item.substring(0,slash),name=item.substring(slash+1);
            if(!pkg.equals(owner))continue;
            if(name.startsWith("."))name=owner+name;
            if(cls.equals(name))return true;
        }
        return false;
    }
    static Status resolve(boolean readable,boolean listed,boolean globalEnabled,boolean connected){
        if(!readable)return Status.UNKNOWN;
        if(!listed)return Status.DISABLED;
        return globalEnabled&&connected?Status.READY:Status.CONNECTING;
    }
    private PermissionState(){}
}
