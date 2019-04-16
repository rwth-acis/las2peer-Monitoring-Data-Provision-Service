package i5.las2peer.services.mobsos.successModeling;

import i5.las2peer.api.Context;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.serialization.MalformedXMLException;
import i5.las2peer.services.mobsos.successModeling.files.FileBackendException;
import i5.las2peer.services.mobsos.successModeling.successModel.SuccessModel;
import io.swagger.annotations.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;


@Path("/")
@Api
@SwaggerDefinition(
        info = @Info(
                title = "MobSOS Success Modeling",
                version = "0.1",
                description = "<p>This service is part of the MobSOS monitoring concept and provides visualization functionality of the monitored data to the web-frontend.</p>",
                termsOfService = "",
                contact = @Contact(
                        name = "Alexander Neumann",
                        email = "neumann@dbis.rwth-aachen.de"),
                license = @License(
                        name = "MIT",
                        url = "https://github.com/rwth-acis/mobsos-success-modeling/blob/master/LICENSE")))
public class RestApi {
    private MonitoringDataProvisionService service = (MonitoringDataProvisionService) Context.getCurrent()
            .getService();

    /**
     * Returns all stored ( = monitored) nodes.
     *
     * @return an array of node id's
     */
    @SuppressWarnings("unchecked")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/nodes")
    public Response getNodes() {
        JSONObject nodeIds = new JSONObject();

        ResultSet resultSet;
        try {
            service.reconnect();
            resultSet = service.database.query(service.NODE_QUERY);
        } catch (SQLException e) {
            System.out.println("(Get Nodes) The query has lead to an error: " + e);
            return null;
        }
        try {
            while (resultSet.next()) {
                nodeIds.put(resultSet.getString(1), "Location: " + resultSet.getString(2));

            }
        } catch (SQLException e) {
            System.out.println("Problems reading result set: " + e);
        }
        return Response.status(Status.OK).entity(nodeIds.toJSONString()).build();
    }

    /**
     * Visualizes a success model for the given node.
     *
     * @param content JSON String containing:
     *                <ul>
     *                <li>nodeName the name of the node</li>
     *                <li>updateMeasures if true, all measures are updated from xml file</li>
     *                <li>updateModels if true, all models are updated from xml file</li>
     *                </ul>
     * @return a HTML representation of the success model
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_HTML)
    @Path("/visualize/nodeSuccessModel")
    public Response visualizeNodeSuccessModel(String content) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject params = (JSONObject) parser.parse(content);

            String nodeName = (String) params.get("nodeName");
            boolean updateMeasures = Boolean.parseBoolean((String) params.get("updateMeasures"));
            boolean updateModels = Boolean.parseBoolean((String) params.get("updateModels"));
            String catalog = (String) params.get("catalog");
            if (updateMeasures) {
                if (service.useFileService) {
                    List<String> measureFiles = service.getMeasureCatalogList();
                    for (String s : measureFiles) {
                        try {
                            service.updateMeasures(s);
                        } catch (MalformedXMLException e) {
                            System.out.println("Measure Catalog seems broken: " + e.getMessage());
                        }
                    }
                } else {
                    try {
                        List<File> filesInFolder = Files.walk(Paths.get(service.catalogFileLocation))
                                .filter(Files::isRegularFile).map(java.nio.file.Path::toFile)
                                .collect(Collectors.toList());
                        for (File f : filesInFolder) {
                            try {
                                System.out.println(f.getName());
                                if (f.getName().endsWith(".xml")) {
                                    service.updateMeasures(f.toString());
                                }
                            } catch (MalformedXMLException e) {
                                System.out.println("Measure Catalog seems broken: " + e.getMessage());
                            }
                        }
                    } catch (IOException e) {
                        System.out.println("Measure Catalog seems broken: " + e.getMessage());
                    }
                }
            }
            if (updateModels) {
                service.knownModels = service.updateModels(catalog);
            }
            return Response.status(Status.OK)
                    .entity(service.visualizeSuccessModel("Node Success Model", nodeName, catalog)).build();
        } catch (ParseException|FileBackendException e1) {
            // TODO Auto-generated catch block
            System.out.println(e1.toString());
            e1.printStackTrace();
        }
        return Response.status(Status.BAD_REQUEST).entity("Error").build();
    }

    /**
     * Visualizes a given service success model.
     *
     * @param content JSON String containing:
     *                <ul>
     *                <li>modelName the name of the success model</li>
     *                <li>updateMeasures if true, all measures are updated from xml file</li>
     *                <li>updateModels if true, all models are updated from xml file</li>
     *                </ul>
     * @return a HTML representation of the success model
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_HTML)
    @Path("/visualize/serviceSuccessModel")
    public Response visualizeServiceSuccessModel(String content) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject params = (JSONObject) parser.parse(content);

            String modelName = (String) params.get("modelName");
            boolean updateMeasures = Boolean.parseBoolean((String) params.get("updateMeasures"));
            boolean updateModels = Boolean.parseBoolean((String) params.get("updateModels"));
            String catalog = (String) params.get("catalog");
            if (updateMeasures) {
                try {
                    if (service.useFileService) {
                        List<String> measureFiles = service.getMeasureCatalogList();
                        for (String s : measureFiles) {
                            try {
                                service.updateMeasures(s);
                            } catch (MalformedXMLException e) {
                                System.out.println("Measure Catalog seems broken: " + e.getMessage());
                            }
                        }
                    } else {
                        List<File> filesInFolder = Files.walk(Paths.get(service.catalogFileLocation))
                                .filter(Files::isRegularFile).map(java.nio.file.Path::toFile)
                                .collect(Collectors.toList());
                        for (File f : filesInFolder) {
                            try {
                                if (f.getName().endsWith(".xml")) {
                                    service.updateMeasures(f.toString());
                                }
                            } catch (MalformedXMLException e) {
                                System.out.println("Measure Catalog seems broken: " + e.getMessage());
                                System.out.println("Measure Catalog seems broken: " + e.getMessage());
                            }
                        }
                    }
                } catch (IOException | FileBackendException e) {
                    System.out.println("Measure Catalog seems broken: " + e.getMessage());
                }
            }
            if (updateModels) {
                service.knownModels = service.updateModels(catalog);
            }
            return Response.status(Status.OK).entity(service.visualizeSuccessModel(modelName, null, catalog))
                    .build();
        } catch (ParseException e1) {
            // TODO Auto-generated catch block
            System.out.println(e1.toString());
            e1.printStackTrace();
        }
        return Response.status(Status.BAD_REQUEST).entity("Error").build();
    }

    /**
     * Gets the names of all known measures. Currently not used by the frontend but can be used in later
     * implementations to make success model creation possible directly through the frontend.
     *
     * @param update if true, the list is read again
     * @return an array of names
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/measures")
    public Response getMeasureNames(@QueryParam("catalog") String catalog, @QueryParam("update") boolean update) {
        if (update) {
            try {
                List<File> filesInFolder = Files.walk(Paths.get(service.catalogFileLocation))
                        .filter(Files::isRegularFile).map(java.nio.file.Path::toFile).collect(Collectors.toList());
                for (File f : filesInFolder) {
                    try {
                        if (f.getName().endsWith(".xml")) {
                            service.measureCatalogs.put(catalog, service.updateMeasures(f.getName()));
                        }
                    } catch (MalformedXMLException e) {
                        System.out.println("Measure Catalog seems broken: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.out.println("Measure Catalog seems broken: " + e.getMessage());
            }
        }
        String[] returnArray = new String[service.measureCatalogs.get(catalog).size()];
        int counter = 0;
        for (String key : service.measureCatalogs.get(catalog).keySet()) {
            returnArray[counter] = key;
            counter++;
        }
        return Response.status(Status.OK).entity(returnArray).build();
    }

    /**
     * Returns all stored ( = monitored) services.
     *
     * @return an array of service agent id
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/services")
    public Response getServices() {
        List<String> monitoredServices = new ArrayList<>();

        ResultSet resultSet;
        try {
            service.reconnect();
            resultSet = service.database.query(service.SERVICE_QUERY);
        } catch (SQLException e) {
            System.out.println("(getServiceIds) The query has lead to an error: " + e);
            return null;
        }
        try {
            while (resultSet.next()) {
                monitoredServices.add(resultSet.getString(2));
            }
        } catch (SQLException e) {
            System.out.println("Problems reading result set: " + e);
        }
        return Response.status(Status.OK).entity(monitoredServices.toArray(new String[monitoredServices.size()]))
                .build();
    }

    /**
     * Returns the name of all stored success models for the given service.
     *
     * @param serviceName the name of the service
     * @param update      updates the available success models with the content of the success model folder
     * @return an array of success model names
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/models")
    public Response getModels(@QueryParam("service") String serviceName, @QueryParam("update") boolean update,
                              @QueryParam("catalog") String catalog) {
        if (update) {
            service.knownModels = service.updateModels(catalog);
        }

        Collection<SuccessModel> models = service.knownModels.values();
        List<String> modelNames = new ArrayList<>();
        for (SuccessModel model : models) {
            if (model.getServiceName() != null && model.getServiceName().equals(serviceName)) {
                modelNames.add(model.getName());
            }
        }
        return Response.status(Status.OK).entity(modelNames.toArray(new String[0])).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/measureCatalogs")
    public Response getMeasureCatalogs() {
        service.startUpdatingMeasures();
        JSONObject catalogs = new JSONObject();
        try {
            List<String> resultList = service.getMeasureCatalogLocations();
            catalogs.put("catalogs", resultList);
            return Response.status(Status.OK).entity(catalogs.toJSONString()).build();
        } catch (Exception e) {
            // one may want to handle some exceptions differently
            e.printStackTrace();
            Context.get().monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
        }
        return Response.status(Status.OK).entity(catalogs.toJSONString()).build();
    }

}