package com.yasserapps.store;
import java.util.Comparator;
/** Uses only upstream reported counts or chart positions; never invents downloads. */
final class Popularity {
    static final Comparator<Catalog.App> ORDER=Comparator
        .comparingInt((Catalog.App a)->a.sourceDownloads>=0?0:a.sourceRank>0?1:2)
        .thenComparing(Comparator.comparingLong((Catalog.App a)->a.sourceDownloads).reversed())
        .thenComparingLong(a->a.sourceRank>0?a.sourceRank:Long.MAX_VALUE)
        .thenComparing(Comparator.comparingLong((Catalog.App a)->a.updatedAt).reversed())
        .thenComparing(a->a.packageName).thenComparing(a->a.id);
    static void inherit(Catalog.App preferred,Catalog.App listing){
        if(preferred.sourceDownloads<0)preferred.sourceDownloads=listing.sourceDownloads;
        if(preferred.sourceRank<=0)preferred.sourceRank=listing.sourceRank;
    }
}
