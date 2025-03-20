package com.gw.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Comparator;

@Entity
public class FeedItem extends BaseListedItem {
    
    private static Logger logger = LogManager.getLogger(FeedItem.class);

    @Id
    private String webTagNumber;
    @Column
    private String webStatus;
    @Column
    private String webPriceRetail;
    @Column
    private String webPriceSale;
    @Column
    private String webPriceEbay;
    @Column
    private String webFlagEbayauction;
    @Column
    private String costInvoiced;

    @Column(length=8192)
    @Lob
    private String webDescriptionShort;

    @Column
    private String webStyle;
    @Column
    private String webDesigner;
    @Column
    private String webWatchModel;
    @Column
    private String webWatchYear;
    @Column
    private String webMetalType;
    @Column
    private String webWatchManufacturerReferenceNumber;
    @Column
    private String webWatchMovement;
    @Column
    private String webWatchCase;
    @Column
    private String webWatchDial;
    @Column
    private String webWatchStrap;
    @Column
    private String webWatchCondition;
    @Column
    private String webWatchDiameter;
    @Column
    private String webWatchBoxPapers;
    @Column
    private String webImagePath1;
    @Column
    private String webImagePath2;
    @Column
    private String webImagePath3;
    @Column
    private String webImagePath4;
    @Column
    private String webImagePath5;
    @Column
    private String webImagePath6;
    @Column
    private String webImagePath7;
    @Column
    private String webImagePath8;
    @Column
    private String webImagePath9;
    @Column 
    private String webPriceChronos;
    @Column
    private String webCategory;

    @Column(length=8192)
    @Lob
    private String webNotes;

    @Column
    private String webPriceKeystone;
    @Column
    private String webSerialNumber;
    @Column
    private String webWatchDialMarkers;
    @Column
    private String webWatchBandMaterial;
    @Column
    private String webWatchBezelType;
    @Column
    private String webWatchCaseCrown;
    @Column
    private String webWatchBandType;
    @Column
    private String webPriceWholesale;
    @Column
    private String webWatchGeneralDial;
    
    
    
    public void copyFrom(FeedItem other) {
        webTagNumber = other.getWebTagNumber();
        webStatus = other.getWebStatus();
        webPriceRetail = other.getWebPriceRetail();
        webPriceSale = other.getWebPriceSale();
        webPriceEbay = other.getWebPriceEbay();
        webFlagEbayauction = other.getWebFlagEbayauction();
        costInvoiced = other.getCostInvoiced();
        webDescriptionShort = other.getWebDescriptionShort();
        webStyle = other.webStyle;
        webDesigner = other.getWebDesigner();
        webWatchModel = other.getWebWatchModel();
        webWatchYear = other.getWebWatchYear();
        webMetalType = other.getWebMetalType();
        webWatchManufacturerReferenceNumber = other.getWebWatchManufacturerReferenceNumber();
        webWatchMovement = other.getWebWatchMovement();
        webWatchCase = other.getWebWatchCase();
        webWatchDial = other.getWebWatchDial();
        webWatchStrap = other.getWebWatchStrap();
        webWatchCondition = other.getWebWatchCondition();
        webWatchDiameter = other.getWebWatchDiameter();
        webWatchBoxPapers = other.getWebWatchBoxPapers();
        webImagePath1 = other.getWebImagePath1();
        webImagePath2 = other.getWebImagePath2();
        webImagePath3 = other.getWebImagePath3();
        webImagePath4 = other.getWebImagePath4();
        webImagePath5 = other.getWebImagePath5();
        webImagePath6 = other.getWebImagePath6();
        webImagePath7 = other.getWebImagePath7();
        webImagePath8 = other.getWebImagePath8();
        webImagePath9 = other.getWebImagePath9();
        webPriceChronos = other.getWebPriceChronos();
        webCategory = other.getWebCategory();
        webNotes = other.getWebNotes();
        webPriceKeystone = other.getWebPriceKeystone();
        
        webSerialNumber = other.webSerialNumber;
        webWatchDialMarkers = other.webWatchDialMarkers;
        webWatchBandMaterial = other.webWatchBandMaterial;
        webWatchBezelType = other.webWatchBezelType;
        webWatchCaseCrown = other.webWatchCaseCrown;
        webWatchBandType = other.webWatchBandType;
        webPriceWholesale = other.webPriceWholesale;
        webWatchGeneralDial = other.webWatchGeneralDial;
    }
    
    public static FeedItem fromRecordNode(Node record){
        //Get a list of field from record
        FeedItem item = new FeedItem();
        NodeList fieldNodeList = record.getChildNodes();
        for (int i=0; i<fieldNodeList.getLength(); i++){
            //each field node has a data node
            Node fieldNode = fieldNodeList.item(i);
            //String fieldName = ((Element)fieldNode).getAttribute("name");
            if ("field".equals(fieldNode.getNodeName())) {
                String fieldName = fieldNode.getAttributes().getNamedItem("name").getNodeValue();
                
                Node dataChildNode  = fieldNode.getFirstChild();
                while (!"data".equals(dataChildNode.getNodeName())) {
                    dataChildNode = dataChildNode.getNextSibling();
                }
                String dataValue = dataChildNode.getTextContent(); 
                
                String fieldDataValue =  StringUtils.trimToNull(dataValue);
                
                if (fieldName.equals("web_tag_number")){
                   item.setWebTagNumber(fieldDataValue);
                 } else if (fieldName.equals("web_description_short")){
                     item.setWebDescriptionShort(fieldDataValue);
                 } else if (fieldName.equals("web_price_retail")){
                     item.setWebPriceRetail(fieldDataValue);
                 } else if (fieldName.equals("web_price_ebay")){
                         item.setWebPriceEbay(fieldDataValue);
                 } else if (fieldName.equals("web_flag_ebayauction")){
                     item.setWebFlagEbayauction(fieldDataValue);
                 } else if (fieldName.equals("web_price_sale")){
                     item.setWebPriceSale(fieldDataValue);
                 } else if (fieldName.equals("web_cost_invoiced")){
                     item.setCostInvoiced(fieldDataValue);
                 } else if (fieldName.equals("web_designer")){
                     item.setWebDesigner(fieldDataValue);
                 } else if (fieldName.equals("web_style")){
                     item.setWebStyle(fieldDataValue);
                 } else if (fieldName.equals("web_metal_type")){
                     item.setWebMetalType(fieldDataValue);
                 } else if (fieldName.equals("web_watch_model")){
                     item.setWebWatchModel(fieldDataValue);
                 } else if (fieldName.equals("web_watch_year")){
                     item.setWebWatchYear(fieldDataValue);
                 } else if (fieldName.equals("web_watch_manufacturer_reference_number")){
                     item.setWebWatchManufacturerReferenceNumber(fieldDataValue);
                 } else if (fieldName.equals("web_watch_movement")){
                     item.setWebWatchMovement(fieldDataValue);
                 } else if (fieldName.equals("web_watch_case")){
                     item.setWebWatchCase(fieldDataValue);
                 } else if (fieldName.equals("web_watch_dial")){
                     item.setWebWatchDial(fieldDataValue);
                 } else if (fieldName.equals("web_watch_strap")){
                     item.setWebWatchStrap(fieldDataValue);
                 } else if (fieldName.equals("web_watch_condition")){
                     item.setWebWatchCondition(fieldDataValue);
                 } else if (fieldName.equals("web_status")){
                     item.setWebStatus(fieldDataValue);
                 } else if (fieldName.equals("web_watch_diameter")) {
                     item.setWebWatchDiameter(fieldDataValue);
                 } else if (fieldName.equals("web_watch_box_papers")) {
                     item.setWebWatchBoxPapers(fieldDataValue);
                 } else if (fieldName.equals("web_image_path_1")){
                     item.setWebImagePath1(fieldDataValue);
                 } else if (fieldName.equals("web_image_path_2")){
                     item.setWebImagePath2(fieldDataValue);
                 } else if (fieldName.equals("web_image_path_3")){
                     item.setWebImagePath3(fieldDataValue);
                 } else if (fieldName.equals("web_image_path_4")){
                     item.setWebImagePath4(fieldDataValue);
                 } else if (fieldName.equals("web_image_path_5")){
                     item.setWebImagePath5(fieldDataValue);
                 } else if (fieldName.equals("web_image_path_6")){
                     item.setWebImagePath6(fieldDataValue);
                 } else if (fieldName.equals("web_image_path_7")){
                     item.setWebImagePath7(fieldDataValue);
                 } else if (fieldName.equals("web_image_path_8")){
                     item.setWebImagePath8(fieldDataValue);
                 } else if (fieldName.equals("web_image_path_9")){
                     item.setWebImagePath9(fieldDataValue);
                 } else if (fieldName.equals("web_price_chronos__c")){
                     item.setWebPriceChronos(fieldDataValue);
                 } else if (fieldName.equals("web_category")){
                     item.setWebCategory(fieldDataValue);
                 } else if (fieldName.equals("web_notes")){
                     item.setWebNotes(fieldDataValue);
                 } else if (fieldName.equals("web_price_keystone")) {
                     item.setWebPriceKeystone(fieldDataValue);
                 } else if (fieldName.equals("web_serial_number")) {
                     item.setWebSerialNumber(fieldDataValue);
                 } else if (fieldName.equals("web_watch_dial_markers")) {
                     item.setWebWatchDialMarkers(fieldDataValue);
                 } else if (fieldName.equals("web_watch_band_material")) {
                     item.setWebWatchBandMaterial(fieldDataValue);
                 } else if (fieldName.equals("web_watch_bezel_type")) {
                     item.setWebWatchBezelType(fieldDataValue);
                 } else if (fieldName.equals("web_watch_case_crown")) {
                     item.setWebWatchCaseCrown(fieldDataValue);
                 } else if (fieldName.equals("web_watch_band_type")) {
                     item.setWebWatchBandType(fieldDataValue);
                 } else if (fieldName.equals("web_price_wholesale")) {
                     item.setWebPriceWholesale(fieldDataValue);
                 } else if (fieldName.equals("web_watch_general_dial")) {
                     item.setWebWatchGeneralDial(fieldDataValue);
                 }
            }
        }
        return item;
    }
    
    /* (non-Javadoc)
     * @see com.gw.IFeedItem#canListOnEbay()
     */
    public boolean canListOnEbay(){
        if (this.webStatus == null) {
            logger.info("Item: " + this.webTagNumber +" can't list because status is null");
            return false;
        }
        
        if (!this.webStatus.equalsIgnoreCase("Available")) {
            logger.info("Item: " + this.webTagNumber +" can't list because status is not Available");
            return false;
        }
        
        if (this.costInvoiced == null) {
            logger.info("Item: " + this.webTagNumber +" can't list because invoice price is null");
        }
        
        return true;
    }
    
    public String getMovementCode() {
        String movement = this.getWebWatchMovement();
        if (movement != null) {
            if (movement.equalsIgnoreCase("Manual")) {
                return "Manual-Wind";
            } else if (movement.equalsIgnoreCase("Automatic")) {
                return "Self-Winding";
            } else if (movement.equalsIgnoreCase("Quartz")) {
                return "Quartz";
            }
        }
        return null;   
    }

    public String getWebTagNumber() {
        return webTagNumber;
    }

    public void setWebTagNumber(String webTagNumber) {
        this.webTagNumber = webTagNumber;
    }

    public String getWebStatus() {
        return webStatus;
    }

    public void setWebStatus(String webStatus) {
        this.webStatus = webStatus;
    }

    public String getWebPriceRetail() {
        return webPriceRetail;
    }

    public void setWebPriceRetail(String webPriceRetail) {
        this.webPriceRetail = webPriceRetail;
    }
    
    public String getWebPriceSale() {
        return webPriceSale;
    }

    public void setWebPriceSale(String webPriceSale) {
        this.webPriceSale = webPriceSale;
    }
    
    public String getWebPriceEbay() {
        return webPriceEbay;
    }
    
    public void setWebPriceEbay(String webPriceEbay) {
        this.webPriceEbay = webPriceEbay;
    }
    
    public String getWebFlagEbayauction() {
        return webFlagEbayauction;
    }
    
    public void setWebFlagEbayauction(String webFlagEbayauction) {
        this.webFlagEbayauction = webFlagEbayauction;
    }

    public String getWebPriceKeystone() {
        return webPriceKeystone;
    }

    public void setWebPriceKeystone(String webPriceKeystone) {
        this.webPriceKeystone = webPriceKeystone;
    }

    public String getCostInvoiced() {
        return costInvoiced;
    }

    public void setCostInvoiced(String costInvoiced) {
        this.costInvoiced = costInvoiced;
    }

    public String getWebDescriptionShort() {
        return webDescriptionShort;
    }

    public void setWebDescriptionShort(String webDescriptionShort) {
        this.webDescriptionShort = webDescriptionShort;
    }

    public String getWebStyle() {
        return webStyle;
    }

    public void setWebStyle(String webStyle) {
        this.webStyle = webStyle;
    }

    public String getWebDesigner() {
        return webDesigner;
    }

    public void setWebDesigner(String webDesigner) {
        this.webDesigner = webDesigner;
    }

    public String getWebWatchModel() {
        return webWatchModel;
    }

    public void setWebWatchModel(String webWatchModel) {
        this.webWatchModel = webWatchModel;
    }

    public String getWebWatchYear() {
        return webWatchYear;
    }

    public void setWebWatchYear(String webWatchYear) {
        this.webWatchYear = webWatchYear;
    }

    public String getWebMetalType() {
        return webMetalType;
    }

    public void setWebMetalType(String webMetalType) {
        this.webMetalType = webMetalType;
    }

    public String getWebWatchManufacturerReferenceNumber() {
        return webWatchManufacturerReferenceNumber;
    }

    public void setWebWatchManufacturerReferenceNumber(String webWatchManufacturerReferenceNumber) {
        this.webWatchManufacturerReferenceNumber = webWatchManufacturerReferenceNumber;
    }

    public String getWebWatchMovement() {
        return webWatchMovement;
    }

    public void setWebWatchMovement(String webWatchMovement) {
        this.webWatchMovement = webWatchMovement;
    }

    public String getWebWatchCase() {
        return webWatchCase;
    }

    public void setWebWatchCase(String webWatchCase) {
        this.webWatchCase = webWatchCase;
    }

    public String getWebWatchDial() {
        return webWatchDial;
    }

    public void setWebWatchDial(String webWatchDial) {
        this.webWatchDial = webWatchDial;
    }

    public String getWebWatchStrap() {
        return webWatchStrap;
    }

    public void setWebWatchStrap(String webWatchStrap) {
        this.webWatchStrap = webWatchStrap;
    }

    public String getWebWatchCondition() {
        return webWatchCondition;
    }

    public void setWebWatchCondition(String webWatchCondition) {
        this.webWatchCondition = webWatchCondition;
    }

    public String getWebWatchDiameter() {
        return webWatchDiameter;
    }

    public void setWebWatchDiameter(String webWatchDiameter) {
        this.webWatchDiameter = webWatchDiameter;
    }

    public String getWebWatchBoxPapers() {
        return webWatchBoxPapers;
    }

    public void setWebWatchBoxPapers(String webWatchBoxPapers) {
        this.webWatchBoxPapers = webWatchBoxPapers;
    }

    public String getWebImagePath1() {
        return webImagePath1;
    }

    public void setWebImagePath1(String webImagePath1) {
        this.webImagePath1 = webImagePath1;
    }

    public String getWebImagePath2() {
        return webImagePath2;
    }

    public void setWebImagePath2(String webImagePath2) {
        this.webImagePath2 = webImagePath2;
    }

    public String getWebImagePath3() {
        return webImagePath3;
    }

    public void setWebImagePath3(String webImagePath3) {
        this.webImagePath3 = webImagePath3;
    }

    public String getWebImagePath4() {
        return webImagePath4;
    }

    public void setWebImagePath4(String webImagePath4) {
        this.webImagePath4 = webImagePath4;
    }

    public String getWebImagePath5() {
        return webImagePath5;
    }

    public void setWebImagePath5(String webImagePath5) {
        this.webImagePath5 = webImagePath5;
    }

    public String getWebImagePath6() {
        return webImagePath6;
    }

    public void setWebImagePath6(String webImagePath6) {
        this.webImagePath6 = webImagePath6;
    }

    public String getWebImagePath7() {
        return webImagePath7;
    }

    public void setWebImagePath7(String webImagePath7) {
        this.webImagePath7 = webImagePath7;
    }

    public String getWebImagePath8() {
        return webImagePath8;
    }

    public void setWebImagePath8(String webImagePath8) {
        this.webImagePath8 = webImagePath8;
    }

    public String getWebImagePath9() {
        return webImagePath9;
    }

    public void setWebImagePath9(String webImagePath9) {
        this.webImagePath9 = webImagePath9;
    }
    
    public String getWebPriceChronos() {
        return webPriceChronos;
    }

    public void setWebPriceChronos(String webPriceChronos) {
        this.webPriceChronos = webPriceChronos;
    }

    public String getWebCategory() {
        return webCategory;
    }

    public void setWebCategory(String webCategory) {
        this.webCategory = webCategory;
    }

    public String getWebNotes() {
        return webNotes;
    }

    public void setWebNotes(String webNotes) {
        this.webNotes = webNotes;
    }

    public String getWebSerialNumber() {
        return webSerialNumber;
    }

    public void setWebSerialNumber(String webSerialNumber) {
        this.webSerialNumber = webSerialNumber;
    }

    public String getWebWatchDialMarkers() {
        return webWatchDialMarkers;
    }

    public void setWebWatchDialMarkers(String webWatchDialMarkers) {
        this.webWatchDialMarkers = webWatchDialMarkers;
    }

    public String getWebWatchBandMaterial() {
        return webWatchBandMaterial;
    }

    public void setWebWatchBandMaterial(String webWatchBandMaterial) {
        this.webWatchBandMaterial = webWatchBandMaterial;
    }

    public String getWebWatchBezelType() {
        return webWatchBezelType;
    }

    public void setWebWatchBezelType(String webWatchBezelType) {
        this.webWatchBezelType = webWatchBezelType;
    }

    public String getWebWatchCaseCrown() {
        return webWatchCaseCrown;
    }

    public void setWebWatchCaseCrown(String webWatchCaseCrown) {
        this.webWatchCaseCrown = webWatchCaseCrown;
    }

    public String getWebWatchBandType() {
        return webWatchBandType;
    }

    public void setWebWatchBandType(String webWatchBandType) {
        this.webWatchBandType = webWatchBandType;
    }


    public String getWebPriceWholesale() {
        return webPriceWholesale;
    }

    public void setWebPriceWholesale(String webPriceWholesale) {
        this.webPriceWholesale = webPriceWholesale;
    }

    public String getWebWatchGeneralDial() {
        return webWatchGeneralDial;
    }

    public void setWebWatchGeneralDial(String webWatchGeneralDial) {
        this.webWatchGeneralDial = webWatchGeneralDial;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((costInvoiced == null) ? 0 : costInvoiced.hashCode());
        result = prime * result + ((webCategory == null) ? 0 : webCategory.hashCode());
        result = prime * result + ((webDescriptionShort == null) ? 0 : webDescriptionShort.hashCode());
        result = prime * result + ((webDesigner == null) ? 0 : webDesigner.hashCode());
        result = prime * result + ((webFlagEbayauction == null) ? 0 : webFlagEbayauction.hashCode());
        result = prime * result + ((webImagePath1 == null) ? 0 : webImagePath1.hashCode());
        result = prime * result + ((webImagePath2 == null) ? 0 : webImagePath2.hashCode());
        result = prime * result + ((webImagePath3 == null) ? 0 : webImagePath3.hashCode());
        result = prime * result + ((webImagePath4 == null) ? 0 : webImagePath4.hashCode());
        result = prime * result + ((webImagePath5 == null) ? 0 : webImagePath5.hashCode());
        result = prime * result + ((webImagePath6 == null) ? 0 : webImagePath6.hashCode());
        result = prime * result + ((webImagePath7 == null) ? 0 : webImagePath7.hashCode());
        result = prime * result + ((webImagePath8 == null) ? 0 : webImagePath8.hashCode());
        result = prime * result + ((webImagePath9 == null) ? 0 : webImagePath9.hashCode());
        result = prime * result + ((webMetalType == null) ? 0 : webMetalType.hashCode());
        result = prime * result + ((webNotes == null) ? 0 : webNotes.hashCode());
        result = prime * result + ((webPriceChronos == null) ? 0 : webPriceChronos.hashCode());
        result = prime * result + ((webPriceEbay == null) ? 0 : webPriceEbay.hashCode());
        result = prime * result + ((webPriceKeystone == null) ? 0 : webPriceKeystone.hashCode());
        result = prime * result + ((webPriceRetail == null) ? 0 : webPriceRetail.hashCode());
        result = prime * result + ((webPriceSale == null) ? 0 : webPriceSale.hashCode());
        result = prime * result + ((webSerialNumber == null) ? 0 : webSerialNumber.hashCode());
        result = prime * result + ((webStatus == null) ? 0 : webStatus.hashCode());
        result = prime * result + ((webStyle == null) ? 0 : webStyle.hashCode());
        result = prime * result + ((webTagNumber == null) ? 0 : webTagNumber.hashCode());
        result = prime * result + ((webWatchBandMaterial == null) ? 0 : webWatchBandMaterial.hashCode());
        result = prime * result + ((webWatchBandType == null) ? 0 : webWatchBandType.hashCode());
        result = prime * result + ((webWatchBezelType == null) ? 0 : webWatchBezelType.hashCode());
        result = prime * result + ((webWatchBoxPapers == null) ? 0 : webWatchBoxPapers.hashCode());
        result = prime * result + ((webWatchCase == null) ? 0 : webWatchCase.hashCode());
        result = prime * result + ((webWatchCaseCrown == null) ? 0 : webWatchCaseCrown.hashCode());
        result = prime * result + ((webWatchCondition == null) ? 0 : webWatchCondition.hashCode());
        result = prime * result + ((webWatchDial == null) ? 0 : webWatchDial.hashCode());
        result = prime * result + ((webWatchDialMarkers == null) ? 0 : webWatchDialMarkers.hashCode());
        result = prime * result + ((webWatchDiameter == null) ? 0 : webWatchDiameter.hashCode());
        result = prime * result
                + ((webWatchManufacturerReferenceNumber == null) ? 0 : webWatchManufacturerReferenceNumber.hashCode());
        result = prime * result + ((webWatchModel == null) ? 0 : webWatchModel.hashCode());
        result = prime * result + ((webWatchMovement == null) ? 0 : webWatchMovement.hashCode());
        result = prime * result + ((webWatchStrap == null) ? 0 : webWatchStrap.hashCode());
        result = prime * result + ((webWatchYear == null) ? 0 : webWatchYear.hashCode());
        result = prime * result + ((webPriceWholesale == null) ? 0 : webPriceWholesale.hashCode());
        result = prime * result + ((webWatchGeneralDial == null) ? 0 : webWatchGeneralDial.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FeedItem other = (FeedItem) obj;
        if (costInvoiced == null) {
            if (other.costInvoiced != null)
                return false;
        } else if (!costInvoiced.equals(other.costInvoiced))
            return false;
        if (webCategory == null) {
            if (other.webCategory != null)
                return false;
        } else if (!webCategory.equals(other.webCategory))
            return false;
        if (webDescriptionShort == null) {
            if (other.webDescriptionShort != null)
                return false;
        } else if (!webDescriptionShort.equals(other.webDescriptionShort))
            return false;
        if (webDesigner == null) {
            if (other.webDesigner != null)
                return false;
        } else if (!webDesigner.equals(other.webDesigner))
            return false;
        if (webFlagEbayauction == null) {
            if (other.webFlagEbayauction != null)
                return false;
        } else if (!webFlagEbayauction.equals(other.webFlagEbayauction))
            return false;
        if (webImagePath1 == null) {
            if (other.webImagePath1 != null)
                return false;
        } else if (!webImagePath1.equals(other.webImagePath1))
            return false;
        if (webImagePath2 == null) {
            if (other.webImagePath2 != null)
                return false;
        } else if (!webImagePath2.equals(other.webImagePath2))
            return false;
        if (webImagePath3 == null) {
            if (other.webImagePath3 != null)
                return false;
        } else if (!webImagePath3.equals(other.webImagePath3))
            return false;
        if (webImagePath4 == null) {
            if (other.webImagePath4 != null)
                return false;
        } else if (!webImagePath4.equals(other.webImagePath4))
            return false;
        if (webImagePath5 == null) {
            if (other.webImagePath5 != null)
                return false;
        } else if (!webImagePath5.equals(other.webImagePath5))
            return false;
        if (webImagePath6 == null) {
            if (other.webImagePath6 != null)
                return false;
        } else if (!webImagePath6.equals(other.webImagePath6))
            return false;
        if (webImagePath7 == null) {
            if (other.webImagePath7 != null)
                return false;
        } else if (!webImagePath7.equals(other.webImagePath7))
            return false;
        if (webImagePath8 == null) {
            if (other.webImagePath8 != null)
                return false;
        } else if (!webImagePath8.equals(other.webImagePath8))
            return false;
        if (webImagePath9 == null) {
            if (other.webImagePath9 != null)
                return false;
        } else if (!webImagePath9.equals(other.webImagePath9))
            return false;
        if (webMetalType == null) {
            if (other.webMetalType != null)
                return false;
        } else if (!webMetalType.equals(other.webMetalType))
            return false;
        if (webNotes == null) {
            if (other.webNotes != null)
                return false;
        } else if (!webNotes.equals(other.webNotes))
            return false;
        if (webPriceChronos == null) {
            if (other.webPriceChronos != null)
                return false;
        } else if (!webPriceChronos.equals(other.webPriceChronos))
            return false;
        if (webPriceEbay == null) {
            if (other.webPriceEbay != null)
                return false;
        } else if (!webPriceEbay.equals(other.webPriceEbay))
            return false;
        if (webPriceKeystone == null) {
            if (other.webPriceKeystone != null)
                return false;
        } else if (!webPriceKeystone.equals(other.webPriceKeystone))
            return false;
        if (webPriceRetail == null) {
            if (other.webPriceRetail != null)
                return false;
        } else if (!webPriceRetail.equals(other.webPriceRetail))
            return false;
        if (webPriceSale == null) {
            if (other.webPriceSale != null)
                return false;
        } else if (!webPriceSale.equals(other.webPriceSale))
            return false;
        if (webSerialNumber == null) {
            if (other.webSerialNumber != null)
                return false;
        } else if (!webSerialNumber.equals(other.webSerialNumber))
            return false;
        if (webStatus == null) {
            if (other.webStatus != null)
                return false;
        } else if (!webStatus.equals(other.webStatus))
            return false;
        if (webStyle == null) {
            if (other.webStyle != null)
                return false;
        } else if (!webStyle.equals(other.webStyle))
            return false;
        if (webTagNumber == null) {
            if (other.webTagNumber != null)
                return false;
        } else if (!webTagNumber.equals(other.webTagNumber))
            return false;
        if (webWatchBandMaterial == null) {
            if (other.webWatchBandMaterial != null)
                return false;
        } else if (!webWatchBandMaterial.equals(other.webWatchBandMaterial))
            return false;
        if (webWatchBandType == null) {
            if (other.webWatchBandType != null)
                return false;
        } else if (!webWatchBandType.equals(other.webWatchBandType))
            return false;
        if (webWatchBezelType == null) {
            if (other.webWatchBezelType != null)
                return false;
        } else if (!webWatchBezelType.equals(other.webWatchBezelType))
            return false;
        if (webWatchBoxPapers == null) {
            if (other.webWatchBoxPapers != null)
                return false;
        } else if (!webWatchBoxPapers.equals(other.webWatchBoxPapers))
            return false;
        if (webWatchCase == null) {
            if (other.webWatchCase != null)
                return false;
        } else if (!webWatchCase.equals(other.webWatchCase))
            return false;
        if (webWatchCaseCrown == null) {
            if (other.webWatchCaseCrown != null)
                return false;
        } else if (!webWatchCaseCrown.equals(other.webWatchCaseCrown))
            return false;
        if (webWatchCondition == null) {
            if (other.webWatchCondition != null)
                return false;
        } else if (!webWatchCondition.equals(other.webWatchCondition))
            return false;
        if (webWatchDial == null) {
            if (other.webWatchDial != null)
                return false;
        } else if (!webWatchDial.equals(other.webWatchDial))
            return false;
        if (webWatchDialMarkers == null) {
            if (other.webWatchDialMarkers != null)
                return false;
        } else if (!webWatchDialMarkers.equals(other.webWatchDialMarkers))
            return false;
        if (webWatchDiameter == null) {
            if (other.webWatchDiameter != null)
                return false;
        } else if (!webWatchDiameter.equals(other.webWatchDiameter))
            return false;
        if (webWatchManufacturerReferenceNumber == null) {
            if (other.webWatchManufacturerReferenceNumber != null)
                return false;
        } else if (!webWatchManufacturerReferenceNumber.equals(other.webWatchManufacturerReferenceNumber))
            return false;
        if (webWatchModel == null) {
            if (other.webWatchModel != null)
                return false;
        } else if (!webWatchModel.equals(other.webWatchModel))
            return false;
        if (webWatchMovement == null) {
            if (other.webWatchMovement != null)
                return false;
        } else if (!webWatchMovement.equals(other.webWatchMovement))
            return false;
        if (webWatchStrap == null) {
            if (other.webWatchStrap != null)
                return false;
        } else if (!webWatchStrap.equals(other.webWatchStrap))
            return false;
        if (webWatchYear == null) {
            if (other.webWatchYear != null)
                return false;
        } else if (!webWatchYear.equals(other.webWatchYear))
            return false;
        if (webPriceWholesale == null) {
            if (other.webPriceWholesale != null)
                return false;
        } else if (!webPriceWholesale.equals(other.webPriceWholesale))
            return false;
        if (webWatchGeneralDial == null) {
            if (other.webWatchGeneralDial != null)
                return false;
        } else if (!webWatchGeneralDial.equals(other.webWatchGeneralDial))
            return false;
        
        return true;
    }

    public boolean equalsForShopify(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FeedItem other = (FeedItem) obj;
        if (webDesigner == null) {
            if (other.webDesigner != null)
                return false;
        } else if (!webDesigner.equals(other.webDesigner))
            return false;
        
        if (webCategory == null) {
            if (other.webCategory != null)
                return false;
        } else if (!webCategory.equals(other.webCategory))
            return false;
        
        if (webDescriptionShort == null) {
            if (other.webDescriptionShort != null)
                return false;
        } else if (!webDescriptionShort.equals(other.webDescriptionShort))
            return false;
        
        if (webImagePath1 == null) {
            if (other.webImagePath1 != null)
                return false;
        } else if (!webImagePath1.equals(other.webImagePath1))
            return false;
        if (webImagePath2 == null) {
            if (other.webImagePath2 != null)
                return false;
        } else if (!webImagePath2.equals(other.webImagePath2))
            return false;
        if (webImagePath3 == null) {
            if (other.webImagePath3 != null)
                return false;
        } else if (!webImagePath3.equals(other.webImagePath3))
            return false;
        if (webImagePath4 == null) {
            if (other.webImagePath4 != null)
                return false;
        } else if (!webImagePath4.equals(other.webImagePath4))
            return false;
        if (webImagePath5 == null) {
            if (other.webImagePath5 != null)
                return false;
        } else if (!webImagePath5.equals(other.webImagePath5))
            return false;
        if (webImagePath6 == null) {
            if (other.webImagePath6 != null)
                return false;
        } else if (!webImagePath6.equals(other.webImagePath6))
            return false;
        if (webImagePath7 == null) {
            if (other.webImagePath7 != null)
                return false;
        } else if (!webImagePath7.equals(other.webImagePath7))
            return false;
        if (webImagePath8 == null) {
            if (other.webImagePath8 != null)
                return false;
        } else if (!webImagePath8.equals(other.webImagePath8))
            return false;
        if (webImagePath9 == null) {
            if (other.webImagePath9 != null)
                return false;
        } else if (!webImagePath9.equals(other.webImagePath9))
            return false;
        
        if (webMetalType == null) {
            if (other.webMetalType != null)
                return false;
        } else if (!webMetalType.equals(other.webMetalType))
            return false;
        
        if (webNotes == null) {
            if (other.webNotes != null)
                return false;
        } else if (!webNotes.equals(other.webNotes))
            return false;
        
        if (webPriceEbay == null) {
            if (other.webPriceEbay != null)
                return false;
        } else if (!webPriceEbay.equals(other.webPriceEbay))
            return false;
        if (webPriceKeystone == null) {
            if (other.webPriceKeystone != null)
                return false;
        } else if (!webPriceKeystone.equals(other.webPriceKeystone))
            return false;
        
        if (webStatus == null) {
            if (other.webStatus != null)
                return false;
        } else if (!webStatus.equals(other.webStatus))
            return false;
        if (webStyle == null) {
            if (other.webStyle != null)
                return false;
        } else if (!webStyle.equals(other.webStyle))
            return false;
        
        if (webWatchBoxPapers == null) {
            if (other.webWatchBoxPapers != null)
                return false;
        } else if (!webWatchBoxPapers.equals(other.webWatchBoxPapers))
            return false;
        

        if (webWatchCondition == null) {
            if (other.webWatchCondition != null)
                return false;
        } else if (!webWatchCondition.equals(other.webWatchCondition))
            return false;

        if (webWatchDial == null) {
            if (other.webWatchDial != null)
                return false;
        } else if (!webWatchDial.equals(other.webWatchDial))
            return false;
        
        
        if (webWatchDiameter == null) {
            if (other.webWatchDiameter != null)
                return false;
        } else if (!webWatchDiameter.equals(other.webWatchDiameter))
            return false;
        
        if (webWatchManufacturerReferenceNumber == null) {
            if (other.webWatchManufacturerReferenceNumber != null)
                return false;
        } else if (!webWatchManufacturerReferenceNumber.equals(other.webWatchManufacturerReferenceNumber))
            return false;
        
        if (webWatchModel == null) {
            if (other.webWatchModel != null)
                return false;
        } else if (!webWatchModel.equals(other.webWatchModel))
            return false;
        
        if (webWatchMovement == null) {
            if (other.webWatchMovement != null)
                return false;
        } else if (!webWatchMovement.equals(other.webWatchMovement))
            return false;
        
        if (webWatchStrap == null) {
            if (other.webWatchStrap != null)
                return false;
        } else if (!webWatchStrap.equals(other.webWatchStrap))
            return false;
        
        if (webWatchYear == null) {
            if (other.webWatchYear != null)
                return false;
        } else if (!webWatchYear.equals(other.webWatchYear))
            return false;
        
        return true;
    }
    
    
    @Override
    public String toString() {
        return "FeedItem [webTagNumber=" + webTagNumber + ", webStatus=" + webStatus + ", webPriceRetail="
                + webPriceRetail + ", webPriceSale=" + webPriceSale + ", webPriceEbay=" + webPriceEbay
                + ", webFlagEbayauction=" + webFlagEbayauction + ", costInvoiced=" + costInvoiced
                + ", webDescriptionShort=" + webDescriptionShort + ", webStyle=" + webStyle + ", webDesigner="
                + webDesigner + ", webWatchModel=" + webWatchModel + ", webWatchYear=" + webWatchYear
                + ", webMetalType=" + webMetalType + ", webWatchManufacturerReferenceNumber="
                + webWatchManufacturerReferenceNumber + ", webWatchMovement=" + webWatchMovement + ", webWatchCase="
                + webWatchCase + ", webWatchDial=" + webWatchDial + ", webWatchStrap=" + webWatchStrap
                + ", webWatchCondition=" + webWatchCondition + ", webWatchDiameter=" + webWatchDiameter
                + ", webWatchBoxPapers=" + webWatchBoxPapers + ", webImagePath1=" + webImagePath1 + ", webImagePath2="
                + webImagePath2 + ", webImagePath3=" + webImagePath3 + ", webImagePath4=" + webImagePath4
                + ", webImagePath5=" + webImagePath5 + ", webImagePath6=" + webImagePath6 + ", webImagePath7="
                + webImagePath7 + ", webImagePath8=" + webImagePath8 + ", webImagePath9=" + webImagePath9
                + ", webPriceChronos=" + webPriceChronos + ", webCategory=" + webCategory + ", webNotes=" + webNotes
                + ", webPriceKeystone=" + webPriceKeystone + ", webSerialNumber=" + webSerialNumber
                + ", webWatchDialMarkers=" + webWatchDialMarkers + ", webWatchBandMaterial=" + webWatchBandMaterial
                + ", webWatchBezelType=" + webWatchBezelType + ", webWatchCaseCrown=" + webWatchCaseCrown
                + ", webWatchBandType=" + webWatchBandType + ", webPriceWholesale=" + webPriceWholesale
                + ", webWatchGeneralDial=" + webWatchGeneralDial + "]";
    }

    public static Comparator<FeedItem> FeedItemSortBySkuAscComparator = new Comparator<FeedItem>() {

        public int compare(FeedItem item1, FeedItem item2) {
            int item1Sku = Integer.parseInt(item1.getWebTagNumber());
            int item2Sku = Integer.parseInt(item2.getWebTagNumber());
            
            return item1Sku - item2Sku;
        }
    };

    public static Comparator<FeedItem> FeedItemSortBySkuDscComparator = new Comparator<FeedItem>() {

        public int compare(FeedItem item1, FeedItem item2) {
            int item1Sku = Integer.parseInt(item1.getWebTagNumber());
            int item2Sku = Integer.parseInt(item2.getWebTagNumber());

            return item2Sku - item1Sku;
        }
    };

    public int getImageCount() {
        int i = 0;
        if (!StringUtils.isEmpty(this.webImagePath1)) i++;
        if (!StringUtils.isEmpty(this.webImagePath2)) i++;
        if (!StringUtils.isEmpty(this.webImagePath3)) i++;
        if (!StringUtils.isEmpty(this.webImagePath4)) i++;
        if (!StringUtils.isEmpty(this.webImagePath5)) i++;
        if (!StringUtils.isEmpty(this.webImagePath6)) i++;
        if (!StringUtils.isEmpty(this.webImagePath7)) i++;
        if (!StringUtils.isEmpty(this.webImagePath8)) i++;
        if (!StringUtils.isEmpty(this.webImagePath9)) i++;
        return i;
    }
}
