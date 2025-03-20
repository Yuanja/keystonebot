package com.gw.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
 
public abstract class AbstractDao {
    protected EntityManager entityManager;
    
    public EntityManager getEntityManager() {
        return entityManager;
    }
    
    @PersistenceContext
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
    
 
    public void persist(Object entity) {
        getEntityManager().persist(entity);
    }
 
    public void delete(Object entity) {
        getEntityManager().remove(entity);
    }
}