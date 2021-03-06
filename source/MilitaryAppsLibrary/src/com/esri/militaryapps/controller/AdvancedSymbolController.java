package com.esri.militaryapps.controller;

import com.esri.militaryapps.model.Geomessage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A controller for ArcGIS Runtime advanced symbology. Use this class when you want to use
 * MessageGroupLayer, MessageProcessor, SymbolDictionary, and MIL-STD-2525C symbols.
 */
public abstract class AdvancedSymbolController {
    
    private static final Logger logger = Logger.getLogger(AdvancedSymbolController.class.getName());
    
    private final MapController mapController;
    private final Map<String, Geomessage> geomessages = new HashMap<String, Geomessage>();
    private final HashSet<String> highlightedIds = new HashSet<String>();
    private final HashMap<String, Integer> spotReportIdToGraphicId = new HashMap<String, Integer>();
    
    private boolean showLabels = true;
    private Set<String> messageTypesSupported = null;
    
    /**
     * Instantiates a new AdvancedSymbolController.
     * @param mapController the associated MapController.
     */
    public AdvancedSymbolController(MapController mapController) {
        this.mapController = mapController;
    }
    
    /**
     * Returns an array of supported message types (spot reports, position reports,
     * etc.) for your implementation. For example, in the ArcGIS Runtime SDK for
     * Android, you can call MessageProcessor.getMessageTypesSupported() in your
     * implementation of this method.
     * @return an array of supported message types.
     */
    public abstract String[] getMessageTypesSupported();
    
    /**
     * Returns the action property name for your implementation's message processor.
     * This value is usually "_Action" or "_action".
     * @return the action property name for your implementation's message processor.
     */
    public abstract String getActionPropertyName();
    
    /**
     * Returns your implementation's message type name for the given Geomessage
     * type name.
     * @param geomessageTypeName the Geomessage type name.
     * @return your implementation's message type name for the given Geomessage
     * type name.
     */
    protected abstract String translateMessageTypeName(String geomessageTypeName);
    
    private boolean messageTypeExists(String messageType) {
        if (null == messageTypesSupported) {
            messageTypesSupported = new HashSet<String>(Arrays.asList(getMessageTypesSupported()));
        }
        
        return messageTypesSupported.contains(messageType);
    }
    
    /**
     * Translates a Geomessage chem light color string (1, 2, 3, 4) to a chem light
     * color string for your implementation.
     * @param geomessageColorString a Geomessage chem light color string. "1" is
     *                              for red, "2" is for green, "3" is for blue,
     *                              and "4" is for yellow.
     * @return a chem light color string for your implementation.
     */
    protected abstract String translateColorString(String geomessageColorString);
    
    /**
     * 
     * @param x
     * @param y
     * @param wkid
     * @param graphicId the graphic ID for the existing graphic; get this from spotReportIdToGraphicId.
     *                  Use null if this is a new report.
     * @param geomessage
     * @return the graphic ID for the created or updated graphic, or null if the
     *         graphic could not be displayed.
     */
    protected abstract Integer displaySpotReport(
            double x,
            double y,
            int wkid,
            Integer graphicId,
            Geomessage geomessage);
        
    /**
     * Processes a Geomessage, adding, modifying, or removing a symbol on the map
     * if appropriate.
     * @param geomessage the Geomessage to process.
     */
    protected void processGeomessage(Geomessage geomessage) {
        if ("spotrep".equals(geomessage.getProperty(Geomessage.TYPE_FIELD_NAME))
                || "spot_report".equals(geomessage.getProperty(Geomessage.TYPE_FIELD_NAME))) {
            //Use a single symbol for all spot reports
            String controlPointsString = (String) geomessage.getProperty(Geomessage.CONTROL_POINTS_FIELD_NAME);
            if (null != controlPointsString) {
                StringTokenizer tok = new StringTokenizer(controlPointsString, ",");
                if (2 == tok.countTokens()) {
                    double x = Double.parseDouble(tok.nextToken());
                    double y = Double.parseDouble(tok.nextToken());
                    int wkid = Integer.parseInt((String) geomessage.getProperty(Geomessage.WKID_FIELD_NAME));
                    Integer currentGraphicId = spotReportIdToGraphicId.get(geomessage.getId());
                    int newGraphicId = displaySpotReport(x, y, wkid, currentGraphicId, geomessage);
                    if (null == currentGraphicId || currentGraphicId != newGraphicId) {
                        spotReportIdToGraphicId.put(geomessage.getId(), newGraphicId);
                    }
                }
            }
            if ("remove".equalsIgnoreCase((String) geomessage.getProperty(getActionPropertyName()))) {
                synchronized (spotReportIdToGraphicId) {
                    spotReportIdToGraphicId.remove(geomessage.getId());
                }
            }
        } else {
            //Let the MessageProcessor handle other types of reports

            /**
             * Translate from an AFM message type name to a message type name for
             * your implementation.
             */
            String messageType = (String) geomessage.getProperty(Geomessage.TYPE_FIELD_NAME);
            if (!messageTypeExists(messageType)) {
                geomessage.setProperty(Geomessage.TYPE_FIELD_NAME, translateMessageTypeName(messageType));
            }
            
            /**
             * Translate from a Geomessage color string to a color string for your
             * implementation.
             */
            if ("chemlight".equals(geomessage.getProperty(Geomessage.TYPE_FIELD_NAME))) {
                String colorString = (String) geomessage.getProperty("color");
                if (null == colorString) {
                    colorString = (String) geomessage.getProperty("chemlight");
                }
                colorString = translateColorString(colorString);
                if (null != colorString) {
                    geomessage.setProperty("chemlight", colorString);
                }
            }
            
            //Workaround for https://github.com/Esri/squad-leader-android/issues/63
            //TODO remove this workaround when the issue is fixed in ArcGIS Runtime
            if (isShowLabels() && geomessage.getProperties().containsKey("datetimevalid")) {
                if (!geomessage.getProperties().containsKey("z")) {
                    geomessage.setProperty("z", "0");
                }
                String controlPoints = (String) geomessage.getProperty(Geomessage.CONTROL_POINTS_FIELD_NAME);
                if (null != controlPoints) {
                    StringTokenizer tok = new StringTokenizer(controlPoints, ",; ");
                    if (2 <= tok.countTokens()) {
                        try {
                            Double x = Double.parseDouble(tok.nextToken());
                            Double y = Double.parseDouble(tok.nextToken());
                            String wkid = (String) geomessage.getProperty(Geomessage.WKID_FIELD_NAME);
                            if (null != wkid) {
                                double[] lonLat = mapController.projectPoint(x, y, Integer.parseInt(wkid), 4326);
                                x = lonLat[0];
                                y = lonLat[1];
                            }
                            geomessage.setProperty("x", x);
                            geomessage.setProperty("y", y);
                        } catch (NumberFormatException nfe) {
                            logger.log(Level.SEVERE, "_control_points or WKID NumberFormatException", nfe);
                        }
                    }
                }
            }
            

            if (!isShowLabels()) {
                geomessage = getGeomessageWithoutLabels(geomessage);
            }
            
            processMessage(geomessage);
            
            boolean needToHighlight = false;
            boolean needToUnhighlight = false;
            boolean previouslyHighlighted = highlightedIds.contains(geomessage.getId());
            boolean nowHighlighted = "1".equals(geomessage.getProperty("status911"));
            if (previouslyHighlighted) {
                needToUnhighlight = !nowHighlighted;
            } else {
                needToHighlight = nowHighlighted;
            }
            if (needToHighlight || needToUnhighlight) {
                processHighlightMessage(
                        geomessage.getId(),
                        (String) geomessage.getProperty(Geomessage.TYPE_FIELD_NAME),
                        needToHighlight);                
                if (needToHighlight) {
                    highlightedIds.add(geomessage.getId());
                } else {
                    highlightedIds.remove(geomessage.getId());
                }
            }
        }
        
        if ("remove".equalsIgnoreCase((String) geomessage.getProperty(getActionPropertyName()))) {
            synchronized (geomessages) {
                geomessages.remove(geomessage.getId());
            }
        }
    }
    
    /**
     * Takes a Geomessage and processes it in an implementation-specific way, probably
     * displaying it on the map (unless it's a "remove" Geomessage).
     * @param message
     * @return true if successful.
     */
    protected abstract boolean processMessage(Geomessage message);
    
    /**
     * Highlights the Geomessage with the specified ID.
     * @param geomessageId the Geomessage ID.
     * @param messageType the message type (position_report, etc.).
     * @param highlight true if the message should be highlighted and false if the
     *                  message should be un-highlighted.
     * @return true if successful.
     */
    protected abstract boolean processHighlightMessage(
            String geomessageId,
            String messageType,
            boolean highlight);
    
    /**
     * Creates a "remove" Geomessage with the specified ID and processes it, effectively
     * removing it from the map.
     * @param geomessageId the ID for the Geomessage to be removed from the map.
     * @param messageType the message type.
     */
    protected abstract void processRemoveGeomessage(String geomessageId, String messageType);

    /**
     * Handles a Geomessage, taking the appropriate actions to display, update, remove,
     * highlight, or un-highlight an advanced symbol on the map.
     * @param geomessage the Geomessage to handle.
     */
    public void handleGeomessage(Geomessage geomessage) {
        if (!"remove".equalsIgnoreCase((String) geomessage.getProperty(getActionPropertyName()))) {
            synchronized (geomessages) {
                geomessages.put(geomessage.getId(), geomessage);
            }
        }
        processGeomessage(geomessage);
    }
    
    /**
     * Returns true if labels display on advanced symbology.
     * @return true if labels display on advanced symbology.
     */
    public boolean isShowLabels() {
        return showLabels;
    }

    /**
     * Sets whether labels should display on advanced symbology.
     * @param showLabels true if labels should display on advanced symbology.
     */
    public void setShowLabels(boolean showLabels) {
        if (this.showLabels != showLabels) {
            synchronized (geomessages) {
                Iterator<Geomessage> iter = geomessages.values().iterator();
                while (iter.hasNext()) {
                    Geomessage mess = iter.next();
                    processRemoveGeomessage(mess.getId(), (String) mess.getProperty(Geomessage.TYPE_FIELD_NAME));
                    highlightedIds.remove(mess.getId());
                }
                
                this.showLabels = showLabels;

                iter = geomessages.values().iterator();
                while (iter.hasNext()) {
                    Geomessage mess = iter.next();
                    processGeomessage(mess);
                }
            }
        }
    }
    
    /**
     * Clones the Geomessage, removes the label properties from the clone, and returns
     * the clone.
     * @param geomessage a clone of the Geomessage, without the properties that are
     *                   used for labeling.
     */
    public static Geomessage getGeomessageWithoutLabels(Geomessage geomessage) {
        Geomessage clone = geomessage.clone();
        clone.setProperty("additionalinformation", "");
        clone.setProperty("uniquedesignation", "");
        clone.setProperty("speed", "");
        clone.setProperty("type", "");//vehicle type
        clone.setProperty("x", "");
        clone.setProperty("y", "");
        clone.setProperty("z", "");
        clone.setProperty("datetimevalid", "");
        return clone;
    }
    
}
