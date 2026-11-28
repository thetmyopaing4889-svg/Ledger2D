package com.myanmar.cherrydirectcapture;
/** Pure permission state and narrowly scoped component-list helpers. */
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
    static String appendOwn(String list,String pkg,String cls){
        if(containsComponent(list,pkg,cls))return list;
        String own=pkg+"/"+cls;
        return list==null||list.isEmpty()?own:list+(list.endsWith(":")?"":":")+own;
    }
    static Status resolve(boolean readable,boolean listed,boolean globalEnabled,boolean connected){
        if(!readable)return Status.UNKNOWN;
        if(!listed)return Status.DISABLED;
        return globalEnabled&&connected?Status.READY:Status.CONNECTING;
    }
    private PermissionState(){}
}
