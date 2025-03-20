package com.gw.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.gw.domain.FeedItem;
import com.gw.domain.PredefinedCollection;
import com.gw.services.shopifyapi.objects.Collect;
import com.gw.services.shopifyapi.objects.CustomCollection;

public class CollectionUtility {
    
    public static List<PredefinedCollection> getPredefinedCollectionFromCollect(List<Collect> collects,
            Map<PredefinedCollection, CustomCollection> collectionByEnum){
        
        Map<String, PredefinedCollection> enumByCustomCollectionId = 
                collectionByEnum
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        new Function<Map.Entry<PredefinedCollection,CustomCollection>, String>(){
                            public String apply(Map.Entry<PredefinedCollection,CustomCollection> entry) {
                                return entry.getValue().getId();
                            }
                        },
                        c -> c.getKey()
                        ));
        
        List<PredefinedCollection> predefinedCollections = new ArrayList<PredefinedCollection>();
        collects.stream().forEach(c -> {
            if (enumByCustomCollectionId.containsKey(c.getCollectionId())) {
                predefinedCollections.add(enumByCustomCollectionId.get(c.getCollectionId()));
            }
        });
        
        return predefinedCollections;
    }
    
    public static List<Collect> getCollectionForProduct(String productId, FeedItem feedItem, 
            Map<PredefinedCollection, CustomCollection> collectionByEnum) {
        List<Collect> collects = new ArrayList<Collect>();
        
        for (PredefinedCollection collectionEnum : collectionByEnum.keySet()) {
            if (collectionEnum.accepts(feedItem)) {
                Collect newCollect = new Collect();
                newCollect.setProductId(productId);
                newCollect.setCollectionId(collectionByEnum.get(collectionEnum).getId());
                collects.add(newCollect);
            }
        }
        return collects;
    }
}
