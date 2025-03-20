package com.gw.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.gw.domain.PredefinedCollection;
import com.gw.domain.gruenbergwatches.GruenbergWatchesCollections;
import com.gw.services.CollectionUtility;
import com.gw.services.shopifyapi.objects.Collect;
import com.gw.services.shopifyapi.objects.CustomCollection;

public class CollectTest  {

    @Test 
    public void testGetPredefinedCollects() throws Exception {
        
        Map<PredefinedCollection, CustomCollection> collectionByEnum
            = makeCollectionByEnum();

        List<Collect> collects = new ArrayList<Collect>();
        
        collects.add(makeCollect("collect1", "product1",  GruenbergWatchesCollections.Audemars_Piguet.ordinal()));
        collects.add(makeCollect("collect1", "product1", GruenbergWatchesCollections.Cartier.ordinal()));
        collects.add(makeCollect("collect1", "product1", GruenbergWatchesCollections.DIAMOND_WATCHES.ordinal()));
        collects.add(makeCollect("collect1", "product1", GruenbergWatchesCollections.MENS.ordinal()));
        collects.add(makeCollect("collect1", "product1", GruenbergWatchesCollections.UNDER_5000.ordinal()));
        
        List<PredefinedCollection> predefined = CollectionUtility.getPredefinedCollectionFromCollect(collects, collectionByEnum);
        Assert.assertTrue(predefined.size() == collects.size());
        Assert.assertTrue(predefined.contains(GruenbergWatchesCollections.Audemars_Piguet));
        Assert.assertTrue(predefined.contains(GruenbergWatchesCollections.Cartier));
        Assert.assertTrue(predefined.contains(GruenbergWatchesCollections.DIAMOND_WATCHES));
        Assert.assertTrue(predefined.contains(GruenbergWatchesCollections.MENS));
        Assert.assertTrue(predefined.contains(GruenbergWatchesCollections.UNDER_5000));
        
    }
    
    private Map<PredefinedCollection, CustomCollection> makeCollectionByEnum(){
        Map<PredefinedCollection, CustomCollection> collectionByEnum = new 
                HashMap<PredefinedCollection, CustomCollection>();
        for (GruenbergWatchesCollections collection : GruenbergWatchesCollections.values()) {
            CustomCollection cc = new CustomCollection();
            cc.setId("" + collection.ordinal());
            cc.setTitle(collection.getTitle());
            collectionByEnum.put(collection, cc);
        }
        return collectionByEnum;
    }
    
    private Collect makeCollect(String id, String productId, int collectionId) {
        Collect aCollect = new Collect();
        aCollect.setId(id);
        aCollect.setProductId(productId);
        aCollect.setCollectionId(""+collectionId);
        
        return aCollect;
    }
}
