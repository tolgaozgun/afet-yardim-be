package com.afetyardim.afetyardim.service.izmir;

import com.afetyardim.afetyardim.model.*;
import com.afetyardim.afetyardim.service.SiteService;
import com.afetyardim.afetyardim.service.common.SpreadSheetUtils;
import com.afetyardim.afetyardim.service.common.SiteUtils;
import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

import static com.afetyardim.afetyardim.service.common.SiteUtils.compareFloats;

@Service
@RequiredArgsConstructor
@Slf4j
public class IzmirSitesParser {

    private final SpreadSheetUtils spreadSheetUtils;
    private final SiteService siteService;

    private final static String IZMIR_SPREAD_SHEET_ID = "1pAUwGOfuu6mRUnsHs7uQrggAu8GQm6Z-r6M25lgBCNY";
    private final static String IZMIR_SPREAD_SHEET_RANGE = "A1:G100";
    private final static String CITY_NAME = "İzmir";

    public void parseIzmirSpreadsheet() throws IOException {

        log.info("Start Izmir spread sheet parsing");

        Spreadsheet spreadsheet = spreadSheetUtils.getSpreadSheet(IZMIR_SPREAD_SHEET_ID, IZMIR_SPREAD_SHEET_RANGE);
        List<RowData> rows = spreadsheet.getSheets().get(0).getData().get(0).getRowData();
        //Remove header row
        rows.remove(0);
        Collection<Site> izmirSites = siteService.getSites(Optional.of(CITY_NAME), Optional.empty());
        List<Site> newSites = new ArrayList<>();
        int updatedSiteCount = 0;

        for (int i = 0; i < rows.size(); i++) {
            RowData rowData = rows.get(i);
            try {

                String district = (String) rowData.getValues().get(0).get("formattedValue");
                String siteName = (String) rowData.getValues().get(1).get("formattedValue");
                if (siteName == null || district == null) {
                    continue;
                }
                Optional<Site> existingSite = SiteUtils.findSiteByNameAndDistrict(siteName,district, izmirSites);

                if (existingSite.isEmpty()) {
                    Optional<Site> newSite = createIzmirSite(rowData);
                    if(newSite.isPresent()){
                        newSites.add(newSite.get());
                    }

                } else {
                    updateSite(rowData, existingSite.get());
                    updatedSiteCount++;
                }
            }catch (Exception exception){
                log.warn("Error while parsing row, {}",rowData,exception);
            }
        }

        log.info("Total rows in excel: {}, Total sites in db before: {}, Created site count: {}, Updated site count: {}"
            ,rows.size(),izmirSites.size(),newSites.size(),updatedSiteCount);
        izmirSites.addAll(newSites);
        siteService.saveAllSites(izmirSites);

    }

    public void updateSite(RowData rowData, Site site) {

        String phone = (String) rowData.getValues().get(3).get("formattedValue");
        if (phone != null) {
            site.setContactInformation(phone);
        }
        String lastUpdateTime = (String) rowData.getValues().get(4).get("formattedValue");

        String needStatusText = (String) rowData.getValues().get(5).get("formattedValue");
        Color needStatusColor = null;
        try {
             needStatusColor = rowData.getValues().get(5).getUserEnteredFormat().getBackgroundColor();
        }catch (Exception exception){
            log.warn("Error while parsing need status column color for izmir site: {}",site.getName());
        }
        SiteStatus.SiteStatusLevel needLevel = convertToSiteStatusLevelForIzmir(needStatusColor);


        String note ="";
        try {
            note = (String) rowData.getValues().get(6).get("formattedValue");
        }catch(Exception exception){
            log.warn("Error while parsing note column for izmir site: {}",site.getName());
        }


        List<SiteStatus> newSiteStatuses = generateSiteStatus(needLevel);
        site.setLastSiteStatuses(newSiteStatuses);
        site.setActive(needLevel == SiteStatus.SiteStatusLevel.NEED_REQUIRED ||
            needLevel == SiteStatus.SiteStatusLevel.URGENT_NEED_REQUIRED ? true : false);

        Optional<SiteUpdate> newSiteUpdate =
            generateNewSiteUpdate(site, newSiteStatuses, lastUpdateTime, needStatusText, note);
        if (newSiteUpdate.isPresent()) {
            site.getUpdates().add(newSiteUpdate.get());
        }
    }

    private Optional<SiteUpdate> generateNewSiteUpdate(Site site,
                                                       List<SiteStatus> siteStatuses,
                                                       String lastUpdateTime,
                                                       String needStatusText,
                                                       String note) {

        String concatenatedNote = "";
        if (lastUpdateTime != null) {
            concatenatedNote += "(" + lastUpdateTime + ")";
        }
        if (needStatusText != null) {
            concatenatedNote += " - " + needStatusText;
        }
        if (note != null) {
            concatenatedNote += " - " + note;
        }

        if (site.getUpdates().size() != 0 &&
            site.getUpdates().get(site.getUpdates().size() - 1).getUpdate().equals(concatenatedNote)) {
            return Optional.empty();
        }

        SiteUpdate newSiteUpdate = new SiteUpdate();
        newSiteUpdate.setUpdate(concatenatedNote);
        newSiteUpdate.setSiteStatuses(siteStatuses);

        return Optional.of(newSiteUpdate);
    }

    private List<SiteStatus> generateSiteStatus(SiteStatus.SiteStatusLevel needLevel) {

        return List.of(new SiteStatus(SiteStatusType.MATERIAL, needLevel),
            new SiteStatus(SiteStatusType.HUMAN_HELP, needLevel),
            new SiteStatus(SiteStatusType.FOOD, needLevel),
            new SiteStatus(SiteStatusType.PACKAGE, needLevel));
    }

    private SiteStatus.SiteStatusLevel convertToSiteStatusLevelForIzmir(Color color) {

        if (color == null) {
            return SiteStatus.SiteStatusLevel.UNKNOWN;
        }

        // Red, not needed
        if (color.getRed() != null && compareFloats(color.getRed(), Float.valueOf(1.0f))) {

            return SiteStatus.SiteStatusLevel.NO_NEED_REQUIRED;
        }

        // Green, help needed
        if (color.getGreen() != null && compareFloats(color.getGreen(), Float.valueOf(1.0f))) {
            return SiteStatus.SiteStatusLevel.NEED_REQUIRED;
        }
        return SiteStatus.SiteStatusLevel.UNKNOWN;
    }

    public Optional<Site> createIzmirSite(RowData rowData) {
        String siteName = (String) rowData.getValues().get(1).get("formattedValue");
        Optional<Location> location = buildSiteLocation(rowData);
        if (Objects.isNull(siteName)) {
            return Optional.empty();
        }

        if (location.isEmpty()) {
            log.warn("Location is null for {}", siteName);
            return Optional.empty();
        }

        String phone = (String) rowData.getValues().get(3).get("formattedValue");
        Site site = new Site();
        site.setName(siteName);
        site.setActive(false);
        site.setContactInformation(phone);
        site.setVerified(true);
        site.setType(SiteType.SUPPLY);
        site.setLocation(location.get());
        return Optional.of(site);
    }

    private Optional<Location> buildSiteLocation(RowData rowData) {
        String mapUrl = (String) rowData.getValues().get(2).get("formattedValue");

        if (Objects.isNull(mapUrl)) {
            return Optional.empty();
        }
        String district = (String) rowData.getValues().get(0).get("formattedValue");
        Location location = new Location();
        location.setDistrict(district);
        location.setCity(CITY_NAME);
        location.setAdditionalAddress("Bu alana adres tarifi al butonunu kullanınız.");
        try {
            List<Double> coordinates = spreadSheetUtils.getCoordinatesByUrl(mapUrl);
            location.setLatitude(coordinates.get(0));
            location.setLongitude(coordinates.get(1));
        } catch (Exception exception) {
            log.error("Could not get coordinates by map url {}", mapUrl, exception);
            return Optional.empty();
        }
        return Optional.of(location);
    }
}