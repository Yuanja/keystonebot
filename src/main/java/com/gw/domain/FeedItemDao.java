package com.gw.domain;
 
import java.util.Collections;
import java.util.Date;
import java.util.List;

import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Root;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;
@Repository("feedItemDao")
public class FeedItemDao extends AbstractDao {
    final static Logger logger = LogManager.getLogger(FeedItemDao.class);
    
    public void save(FeedItem feedItem) {
        feedItem.setLastUpdatedDate(new Date());
        persist(feedItem);
    }
 
    public List<FeedItem> findAll() {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<FeedItem> q = cb.createQuery(FeedItem.class);
        Root<FeedItem> c = q.from(FeedItem.class);
        q.select(c);
        TypedQuery<FeedItem> query = getEntityManager().createQuery(q);
        try{
            return query.getResultList();
        } catch (NoResultException nre){
            return null;
        }
    }
    
    public void delete(String webRecordId) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaDelete<FeedItem> delete = cb.
           createCriteriaDelete(FeedItem.class);
        
        Root<FeedItem> e = delete.from(FeedItem.class);
        delete.where(cb.equal(e.get("webTagNumber"), webRecordId));
        getEntityManager().createQuery(delete).executeUpdate();
    }
    
    public void deleteAll() {
        String deleteHql = String.format("delete FeedItem");
        getEntityManager().createQuery(deleteHql).executeUpdate();
    }
 
    public FeedItem findByWebTagNumber(String webRecordId){
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<FeedItem> q = cb.createQuery(FeedItem.class);
        Root<FeedItem> c = q.from(FeedItem.class);
        ParameterExpression<String> p = cb.parameter(String.class);
        q.select(c).where(cb.equal(c.get("webTagNumber"), p));
        
        TypedQuery<FeedItem> query = getEntityManager().createQuery(q);
        query.setParameter(p, webRecordId);
        try{
            FeedItem result = query.getSingleResult();
            return result;
        } catch (NoResultException nre){
            return null;
        }
    }
    
    public List<FeedItem> findByWebDescriptionShort(String searchString){
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<FeedItem> q = cb.createQuery(FeedItem.class);
        Root<FeedItem> c = q.from(FeedItem.class);
        ParameterExpression<String> p = cb.parameter(String.class);
        q.select(c).where(cb.like(c.get("webDescriptionShort"), p));
        
        TypedQuery<FeedItem> query = getEntityManager().createQuery(q);
        query.setParameter(p, "%"+searchString+"%");
        try{
            List<FeedItem> result = query.getResultList();
            return result;
        } catch (NoResultException nre){
            return null;
        }
    }
    
    public List<FeedItem> findByStatus(String status){
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<FeedItem> q = cb.createQuery(FeedItem.class);
        Root<FeedItem> c = q.from(FeedItem.class);
        ParameterExpression<String> statusParam = cb.parameter(String.class);
        q.select(c).where( cb.equal(c.get("status"), statusParam));
        
        TypedQuery<FeedItem> query = getEntityManager().createQuery(q);
        query.setParameter(statusParam, status);
        
        try{
            return query.getResultList();
        } catch (NoResultException nre){
            return Collections.emptyList();
        }
    }

    /**
     * Return a list of FeedItem that has publishedDate greater than the minPublishDate param.
     * 
     * @param statuses
     * @param minPublishDate
     * @return
     */
    public List<FeedItem> findByMinLastPublishedDate(Date minPublishDate){
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<FeedItem> q = cb.createQuery(FeedItem.class);
        Root<FeedItem> c = q.from(FeedItem.class);
        ParameterExpression<Date> lastPublishedDateParam = cb.parameter(Date.class);

        q.select(c).where(
            cb.or(
                cb.isNull(c.get("publishedDate")),
                cb.lessThanOrEqualTo(c.get("publishedDate"), lastPublishedDateParam)
            )
        );

        TypedQuery<FeedItem> query = getEntityManager().createQuery(q);
        query.setParameter(lastPublishedDateParam, minPublishDate);
        

        try{
            return query.getResultList();
        } catch (NoResultException nre){
            return Collections.emptyList();
        }
    }

        /**
     * Return a list of FeedItem that has publishedDate greater than the minPublishDate param.
     * 
     * @param statuses
     * @param minPublishDate
     * @return
     */
    public List<FeedItem> findByNotPublished(){
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<FeedItem> q = cb.createQuery(FeedItem.class);
        Root<FeedItem> c = q.from(FeedItem.class);
        q.select(c).where(
                cb.isNull(c.get("publishedDate"))
        );
        TypedQuery<FeedItem> query = getEntityManager().createQuery(q);
        try{
            return query.getResultList();
        } catch (NoResultException nre){
            return Collections.emptyList();
        }
    }
    
    public FeedItem update(FeedItem feedItem){
        feedItem.setLastUpdatedDate(new Date());
        FeedItem mergedItem = getEntityManager().merge(feedItem);
        getEntityManager().flush();
        return mergedItem;
    }
    
    public void deleteByEbayItemId(String ebayItemId) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        // create delete
        CriteriaDelete<FeedItem> delete = cb.
           createCriteriaDelete(FeedItem.class);
        
        // set the root class
        Root<FeedItem> e = delete.from(FeedItem.class);
        // set where clause
        delete.where(cb.equal(e.get("ebayItemId"), ebayItemId));
        // perform update
        getEntityManager().createQuery(delete).executeUpdate();
    }
}
