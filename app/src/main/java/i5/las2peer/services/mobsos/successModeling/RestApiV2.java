package i5.las2peer.services.mobsos.successModeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import i5.las2peer.api.Context;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.api.security.AgentNotFoundException;
import i5.las2peer.api.security.AgentOperationFailedException;
import i5.las2peer.api.security.GroupAgent;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.serialization.MalformedXMLException;
import i5.las2peer.services.mobsos.successModeling.files.FileBackendException;
import i5.las2peer.services.mobsos.successModeling.successModel.Measure;
import i5.las2peer.services.mobsos.successModeling.successModel.MeasureCatalog;
import i5.las2peer.services.mobsos.successModeling.successModel.SuccessModel;
import io.swagger.annotations.*;
import io.swagger.jaxrs.Reader;
import io.swagger.models.Swagger;
import io.swagger.util.Json;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import net.minidev.json.JSONArray;
import net.minidev.json.parser.JSONParser;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.simple.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Path("/apiv2")
@Api
@Produces(MediaType.APPLICATION_JSON)
@SwaggerDefinition(
  info = @Info(
    title = "MobSOS Success Modeling API v2",
    version = "0.1",
    description = "<p>This service is part of the MobSOS monitoring concept and provides visualization functionality of the monitored data to the web-frontend.</p>",
    termsOfService = "",
    contact = @Contact(
      name = "Alexander Neumann",
      email = "neumann@dbis.rwth-aachen.de"
    ),
    license = @License(
      name = "MIT",
      url = "https://github.com/rwth-acis/mobsos-success-modeling/blob/master/LICENSE"
    )
  )
)
public class RestApiV2 {

  private String defaultDatabase = "las2peer";
  private String defaultDatabaseSchema = "LAS2PEERMON";

  private List<String> successDimensions = Arrays.asList(
    "System Quality",
    "Information Quality",
    "Use",
    "User Satisfaction",
    "Individual Impact",
    "Community Impact"
  );

  private static HashMap<String, String> defaultGroupMap = new HashMap<String, String>();
  private static HashMap<String, net.minidev.json.JSONObject> userContext = new HashMap<String, net.minidev.json.JSONObject>();

  @javax.ws.rs.core.Context
  UriInfo uri;

  @javax.ws.rs.core.Context
  SecurityContext securityContext;

  private MonitoringDataProvisionService service = (MonitoringDataProvisionService) Context
    .getCurrent()
    .getService();

  @GET
  public Response getSwagger() throws JsonProcessingException {
    Swagger swagger = (new Reader(new Swagger())).read(this.getClass());
    return Response
      .status(Response.Status.OK)
      .entity(Json.mapper().writeValueAsString(swagger))
      .build();
  }

  // needed for SBManager
  @GET
  @Path("/swagger.json")
  public Response getSwagger2() throws JsonProcessingException {
    Swagger swagger = (new Reader(new Swagger())).read(this.getClass());
    return Response
      .status(Response.Status.OK)
      .entity(Json.mapper().writeValueAsString(swagger))
      .build();
  }

  @GET
  @Path("/services")
  public Response getServices() {
    JSONObject services = new JSONObject();
    ResultSet resultSet;
    try {
      service.reconnect();
      resultSet = service.database.query(service.SERVICE_QUERY);
    } catch (SQLException e) {
      System.out.println("(Get Nodes) The query has lead to an error: " + e);
      return null;
    }
    try {
      while (resultSet.next()) {
        JSONObject serviceInfo = new JSONObject();
        String serviceName = resultSet.getString(2);
        serviceInfo.put("serviceName", serviceName);
        serviceInfo.put("serviceAlias", resultSet.getString(3));
        serviceInfo.put("registrationTime", resultSet.getTimestamp(4));
        services.put(resultSet.getString(1), serviceInfo);
      }
    } catch (SQLException e) {
      System.out.println("Problems reading result set: " + e);
    }
    return Response.status(Response.Status.OK).entity(services).build();
  }

  @GET
  @Path("/groups")
  public Response getGroups() {
    List<GroupDTO> groups = new ArrayList<>();
    ResultSet resultSet;
    try {
      service.reconnect();
      resultSet = service.database.query(service.GROUP_QUERY);
    } catch (SQLException e) {
      System.out.println("(Get Groups) The query has lead to an error: " + e);
      return null;
    }
    try {
      while (resultSet.next()) {
        String groupID = resultSet.getString(1);
        String groupAlias = resultSet.getString(2);
        System.out.println(groupID + groupAlias);
        boolean member = true;
        try {
          member = Context.get().hasAccess(groupID);
        } catch (AgentOperationFailedException | AgentNotFoundException e) {
          System.out.println(
            "Problems fetching membership state: " +
            e +
            "\nSetting it to TRUE. THIS NEEDS TO BE FIXED!"
          );
          e.printStackTrace();
        }

        GroupDTO groupInformation = new GroupDTO(groupID, groupAlias, member);
        // System.out.println(groupID + groupAlias + member);
        groups.add(groupInformation);
      }
    } catch (SQLException e) {
      System.out.println("Problems reading result set: " + e);
    }
    return Response.status(Response.Status.OK).entity(groups).build();
  }

  @POST
  @Path("/groups")
  public Response addGroup(GroupDTO group) {
    checkGroupMembership(group.groupID);
    try {
      service.reconnect();
      try {
        ResultSet resultSet = service.database.query(
          service.GROUP_QUERY_WITH_ID_PARAM,
          Collections.singletonList(group.groupID)
        );
        if (service.database.getRowCount(resultSet) >= 0) {
          return Response
            .status(422)
            .entity("Group with ID " + group.groupID + " already exists")
            .build();
        }
      } catch (SQLException e) {
        return Response.status(500).entity("").build();
      }

      // System.out.println(group.groupID);
      String groupIDHex = DigestUtils.md5Hex(group.groupID);
      // System.out.println(groupIDHex);
      ResultSet groupAgentResult = service.database.query(
        service.AGENT_QUERY_WITH_MD5ID_PARAM,
        Collections.singletonList(groupIDHex)
      );
      if (service.database.getRowCount(groupAgentResult) == 0) {
        service.database.queryWithDataManipulation(
          service.GROUP_AGENT_INSERT,
          Collections.singletonList(groupIDHex)
        );
      }
      service.database.queryWithDataManipulation(
        service.GROUP_INFORMATION_INSERT,
        Arrays.asList(groupIDHex, group.groupID, group.name)
      );
    } catch (SQLException e) {
      System.out.println("(Add Group) The query has lead to an error: " + e);
      return null;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
    return Response.status(Response.Status.OK).entity(group).build();
  }

  @PUT
  @Path("/groups/changeId")
  public Response modifyGroupId(
    @PathParam("group") String groupName,
    GroupDTO group
  ) {
    checkGroupMembership(group.groupID); //check that user is part of that new group
    try {
      service.reconnect();
      try {
        ResultSet resultSet = service.database.query(
          service.GROUP_QUERY_WITH_ID_PARAM,
          Collections.singletonList(group.groupID)
        );
        if (service.database.getRowCount(resultSet) >= 0) {
          return Response
            .status(422)
            .entity("Group with ID " + group.groupID + " already exists")
            .build();
        }
      } catch (SQLException e) {
        e.printStackTrace();
        return Response.status(500).entity("").build();
      }

      // System.out.println(group.groupID);
      String groupIDHex = DigestUtils.md5Hex(group.groupID);
      // System.out.println(groupIDHex);
      ResultSet groupAgentResult = service.database.query(
        service.AGENT_QUERY_WITH_MD5ID_PARAM,
        Collections.singletonList(groupIDHex)
      );
      if (service.database.getRowCount(groupAgentResult) == 0) {
        service.database.queryWithDataManipulation(
          service.GROUP_AGENT_INSERT,
          Collections.singletonList(groupIDHex)
        );
      }
      service.database.queryWithDataManipulation(
        service.UPDATE_GROUP_QUERY,
        Arrays.asList(groupIDHex, group.groupID, group.name)
      );
    } catch (SQLException e) {
      System.out.println("(Update Group) The query has lead to an error: " + e);
      return null;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
    return Response.status(Response.Status.OK).entity(group).build();
  }

  @GET
  @Path("/groups/{group}")
  public Response getGroup(@PathParam("group") String group) {
    GroupDTO groupInformation;
    ResultSet resultSet;
    try {
      service.reconnect();
      resultSet =
        service.database.query(
          service.GROUP_QUERY_WITH_ID_PARAM,
          Collections.singletonList(group)
        );
      if (service.database.getRowCount(resultSet) == 0) {
        throw new NotFoundException("Group " + group + " does not exist");
      }
    } catch (SQLException e) {
      System.out.println("(Get Group) The query has lead to an error: " + e);
      return null;
    }
    try {
      resultSet.next(); // Select the first result
      String groupID = resultSet.getString(1);
      String groupAlias = resultSet.getString(2);
      boolean member = Context.get().hasAccess(groupID);
      groupInformation = new GroupDTO(groupID, groupAlias, member);
    } catch (SQLException e) {
      e.printStackTrace();
      System.out.println("Problems reading result set: " + e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    } catch (AgentOperationFailedException | AgentNotFoundException e) {
      System.out.println("Problems fetching membership state: " + e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
    return Response.status(Response.Status.OK).entity(groupInformation).build();
  }

  @GET
  @Path("/groups/{group}/isMember")
  public Response isMemberOfGroup(@PathParam("group") String group) {
    try {
      checkGroupMembership(group);
      return Response.ok().build();
    } catch (ForbiddenException e) {
      return Response.status(Response.Status.OK).entity(e.getMessage()).build();
    }
  }

  private String getGroupIdByName(String name) {
    if (name == null) return null;
    ResultSet resultSet;
    try {
      service.reconnect();
      resultSet =
        service.database.query(
          service.GROUP_QUERY_WITH_NAME_PARAM,
          Collections.singletonList(name)
        );
      if (service.database.getRowCount(resultSet) == 0) {
        throw new NotFoundException("Group " + name + " does not exist");
      }
    } catch (SQLException e) {
      System.out.println("(Get Group) The query has lead to an error: " + e);
      return null;
    }

    try {
      resultSet.next(); // Select the first result
      return resultSet.getString(1);
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return null;
  }

  @PUT
  @Path("/groups/{group}")
  public Response updateGroup(GroupDTO group) {
    checkGroupMembership(group.groupID);
    try {
      service.reconnect();
      ResultSet resultSet = service.database.query(
        service.GROUP_QUERY_WITH_ID_PARAM,
        Collections.singletonList(group.groupID)
      );
      if (service.database.getRowCount(resultSet) == 0) {
        throw new NotFoundException("Group " + group + " does not exist");
      }
      service.database.queryWithDataManipulation(
        service.GROUP_INFORMATION_UPDATE,
        Arrays.asList(group.name, group.groupID)
      );
    } catch (SQLException e) {
      System.out.println("(Put Group) The query has lead to an error: " + e);
      return null;
    }
    return Response.status(Response.Status.OK).entity(null).build();
  }

  @GET
  @Path("/measures")
  public Response getMeasureCatalogs() {
    JSONObject catalogs = new JSONObject();
    try {
      for (String measureFile : service.measureCatalogs.keySet()) {
        String group = service.getMeasureCatalogGroup(measureFile);
        if (group != null) catalogs.put(group, getGroupMeasureUri(group));
      }
    } catch (Exception e) {
      // one may want to handle some exceptions differently
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return Response
      .status(Response.Status.OK)
      .entity(catalogs.toJSONString())
      .build();
  }

  @GET
  @Path("/measures/{group}")
  public Response getMeasureCatalogForGroup(@PathParam("group") String group) {
    try {
      for (String measureFile : service.measureCatalogs.keySet()) {
        String measureGroup = service.getMeasureCatalogGroup(measureFile);
        if (Objects.equals(measureGroup, group)) {
          JSONObject catalog = service.measureCatalogs
            .get(measureFile)
            .toJSON();
          return Response
            .status(Response.Status.OK)
            .entity(catalog.toJSONString())
            .build();
        }
      }
    } catch (Exception e) {
      // one may want to handle some exceptions differently

      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return Response
      .status(Response.Status.NOT_FOUND)
      .entity(new ErrorDTO("No catalog found for group " + group))
      .build();
  }

  @POST
  @Path("/measures/{group}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createMeasureCatalogForGroup(
    @PathParam("group") String group,
    MeasureCatalogDTO measureCatalog
  )
    throws MalformedXMLException, FileBackendException {
    checkGroupMembership(group);
    if (service.getMeasureCatalogByGroup(group) != null) {
      return updateMeasureCatalogForGroup(group, measureCatalog); // measure catalog already exists so we update the existing one
    }
    service.writeMeasureCatalog(measureCatalog.xml, group);
    return Response
      .status(Response.Status.CREATED)
      .entity(service.getMeasureCatalogByGroup(group).toJSON())
      .build();
  }

  @PUT
  @Path("/measures/{group}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateMeasureCatalogForGroup(
    @PathParam("group") String group,
    MeasureCatalogDTO measureCatalog
  )
    throws MalformedXMLException, FileBackendException {
    checkGroupMembership(group);
    if (service.getMeasureCatalogByGroup(group) == null) {
      return createMeasureCatalogForGroup(group, measureCatalog); //if a measure catalog does not exist yet we create a new one
    }
    service.writeMeasureCatalog(measureCatalog.xml, group);
    return Response
      .status(Response.Status.OK)
      .entity(new MeasureCatalogDTO(service.getMeasureCatalogByGroup(group)))
      .build();
  }

  @GET
  @Path("/models")
  public Response handleGetSuccessModels() {
    JSONObject models = getSuccessModels();
    return Response
      .status(Response.Status.OK)
      .entity(models.toJSONString())
      .build();
  }

  public JSONObject getSuccessModels() {
    JSONObject models = new JSONObject();
    try {
      for (String group : service.knownModelsV2.keySet()) {
        if (group != null) models.put(group, getGroupModelsUri(group));
      }
    } catch (Exception e) {
      // one may want to handle some exceptions differently
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return models;
  }

  @GET
  @Path("/models/{group}")
  public Response handleGetSuccessModelsForGroup(
    @PathParam("group") String group
  ) {
    String models = "";
    try {
      models = getSuccessModelsForGroup(group);
    } catch (Exception e) {
      // one may want to handle some exceptions differently
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    if (models == "{}") {
      return Response
        .status(Response.Status.NOT_FOUND)
        .entity(new ErrorDTO("No catalog found for group " + group))
        .build();
    } else {
      return Response.status(Response.Status.OK).entity(models).build();
    }
  }

  public String getSuccessModelsForGroup(String group) throws Exception {
    JSONObject models = new JSONObject();
    if (service.knownModelsV2.containsKey(group)) {
      Map<String, SuccessModel> groupModels = service.knownModelsV2.get(group);
      for (String serviceName : groupModels.keySet()) {
        SuccessModel model = groupModels.get(serviceName);
        models.put(
          model.getServiceName(),
          getGroupModelsUriForService(group, serviceName)
        );
      }
    }
    return models.toJSONString();
  }

  @GET
  @Path("/models/{group}/{service}")
  public Response getSuccessModelsForGroupAndService(
    @PathParam("group") String group,
    @PathParam("service") String serviceName
  ) {
    try {
      if (successModelExists(group, serviceName)) {
        SuccessModel groupModelForService = service.knownModelsV2
          .get(group)
          .get(serviceName);
        return Response
          .status(Response.Status.OK)
          .entity(new SuccessModelDTO(groupModelForService))
          .build();
      }
    } catch (Exception e) {
      // one may want to handle some exceptions differently
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return Response
      .status(Response.Status.NOT_FOUND)
      .entity(
        new ErrorDTO(
          "No catalog found for group " + group + " and service " + serviceName
        )
      )
      .build();
  }

  @POST
  @Path("/models/{group}/{service}")
  public Response createSuccessModelsForGroupAndService(
    @PathParam("group") String group,
    @PathParam("service") String serviceName,
    SuccessModelDTO successModel
  )
    throws MalformedXMLException, FileBackendException {
    checkGroupMembership(group);
    if (successModelExists(group, serviceName)) {
      return updateSuccessModelsForGroupAndService(
        group,
        serviceName,
        successModel
      ); // success model already exists so we update the existing one
    }
    service.writeSuccessModel(successModel.xml, group, serviceName);
    return getSuccessModelsForGroupAndService(group, serviceName);
  }

  @PUT
  @Path("/models/{group}/{service}")
  public Response updateSuccessModelsForGroupAndService(
    @PathParam("group") String group,
    @PathParam("service") String serviceName,
    SuccessModelDTO successModel
  )
    throws MalformedXMLException, FileBackendException {
    checkGroupMembership(group);
    if (!successModelExists(group, serviceName)) {
      return createSuccessModelsForGroupAndService(
        group,
        serviceName,
        successModel
      ); //if a success model does not exist yet we create a new one
    }
    service.writeSuccessModel(successModel.xml, group, serviceName);
    return getSuccessModelsForGroupAndService(group, serviceName);
  }

  @GET
  @Path("/models/{group}/{service}/{measure}")
  public Response handleGetMeasureDataForSuccessModelsAndGroupAndService(
    @PathParam("group") String group,
    @PathParam("service") String serviceName,
    @PathParam("measure") String measureName
  ) {
    try {
      List<String> dbResult = getMeasureDataForSuccessModelsAndGroupAndService(
        group,
        serviceName,
        measureName
      );
      if (dbResult.size() != 0) {
        return Response
          .status(Response.Status.OK)
          .entity(new MeasureDataDTO(dbResult))
          .build();
      }
    } catch (Exception e) {
      // one may want to handle some exceptions differently
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return Response
      .status(Response.Status.NOT_FOUND)
      .entity(
        new ErrorDTO(
          "No measure " +
          measureName +
          " found for group " +
          group +
          " and service " +
          serviceName
        )
      )
      .build();
  }

  public List<String> getMeasureDataForSuccessModelsAndGroupAndService(
    String group,
    String serviceName,
    String measureName
  )
    throws Exception {
    List<String> dbResult = new ArrayList<>();
    MeasureCatalog catalog = service.getMeasureCatalogByGroup(group);
    if (
      successModelExists(group, serviceName) &&
      catalog != null &&
      catalog.getMeasures().containsKey(measureName)
    ) {
      SuccessModel groupModelForService = service.knownModelsV2
        .get(group)
        .get(serviceName);
      Measure measure = catalog.getMeasures().get(measureName);
      ArrayList<String> serviceList = new ArrayList<String>();
      serviceList.add(serviceName);
      measure = this.service.insertService(measure, serviceList);
      dbResult = this.service.getRawMeasureData(measure, serviceList);
    }
    return dbResult;
  }

  @GET
  @Path("/messageDescriptions/{service}")
  public Response getMessageDescriptions(
    @PathParam("service") String serviceName
  ) {
    Map<String, String> messageDescriptions =
      this.service.getCustomMessageDescriptionsForService(serviceName);
    return Response
      .status(Response.Status.OK)
      .entity(messageDescriptions)
      .build();
  }

  private void checkGroupMembership(String group) {
    if (!service.currentUserIsMemberOfGroup(group)) {
      throw new ForbiddenException("User is not member of group " + group);
    }
  }

  private String getGroupMeasureUri(String group) {
    return this.uri.getBaseUri().toString() + "apiv2/measures/" + group;
  }

  private String getGroupModelsUri(String group) {
    return this.uri.getBaseUri().toString() + "apiv2/models/" + group;
  }

  private String getGroupModelsUriForService(String group, String serviceName) {
    return (
      this.uri.getBaseUri().toString() +
      "apiv2/models/" +
      group +
      "/" +
      serviceName
    );
  }

  private boolean successModelExists(String group, String serviceName) {
    return (
      service.knownModelsV2.containsKey(group) &&
      service.knownModelsV2.get(group).containsKey(serviceName)
    );
  }

  private static class GroupDTO {

    public String groupID;
    public String name;
    public boolean isMember;

    GroupDTO() {}

    public GroupDTO(String groupID, String name, boolean isMember) {
      this.groupID = groupID;
      this.name = name;
      this.isMember = isMember;
    }
  }

  private static class MeasureCatalogDTO {

    public String xml;

    MeasureCatalogDTO() {}

    MeasureCatalogDTO(String xml) {
      this.xml = xml;
    }

    MeasureCatalogDTO(MeasureCatalog catalog) {
      this.xml = catalog.getXml();
    }
  }

  public static class SuccessModelDTO {

    public String xml;

    SuccessModelDTO() {}

    SuccessModelDTO(String xml) {
      this.xml = xml;
    }

    SuccessModelDTO(SuccessModel successModel) {
      this.xml = successModel.getXml();
    }
  }

  public static class MeasureDataDTO {

    public List data;

    MeasureDataDTO() {}

    MeasureDataDTO(List xml) {
      this.data = xml;
    }
  }

  private static class ErrorDTO {

    public String message;

    ErrorDTO(String message) {
      this.message = message;
    }
  }

  /**
   * Bot function to get a visualization
   *
   * @param body jsonString containing the query, the Chart type and other
   *             optional parameters
   * @return image to be displayed in chat
   */
  @Path("/listMeasures")
  @POST
  @ApiOperation(value = "Returns the measures in a success model to the user")
  @ApiResponses(
    value = {
      @ApiResponse(code = 200, message = "Executed request successfully."),
    }
  )
  public Response listMeasures(String body) {
    System.out.println(
      "User requesting a list of all measures. \nMessage Body" + body
    );
    JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);

    net.minidev.json.JSONObject chatResponse = new net.minidev.json.JSONObject();
    String chatResponseText = "";

    try {
      net.minidev.json.JSONObject requestObject = (net.minidev.json.JSONObject) p.parse(
        body
      );
      String groupName = requestObject.getAsString("groupName");
      String serviceName = requestObject.getAsString("serviceName");
      String dimension = requestObject.getAsString("dimension");
      String email = requestObject.getAsString("email");
      String channel_id = requestObject.getAsString("channel");

      if (groupName == null) {
        groupName = defaultGroupMap.get(channel_id);
      }
      String groupId = this.getGroupIdByName(groupName);

      if (serviceName == null) {
        chatResponseText +=
          "No service name was defined so the _" +
          service.defaultServiceName +
          "_ service is used\n";
        serviceName = service.defaultServiceName;
      }

      if (groupId == null) {
        chatResponseText +=
          "No group name was defined so the _default_ group is used\n";
        groupId = service.defaultGroupId;
      } else {
        chatResponseText +=
          "Here is the success model for the _" + groupName + "_ group\n";
        GroupDTO group = (GroupDTO) this.getGroup(groupId).getEntity();
        if (!group.isMember) { // check if bot is member of group
          throw new ChatException(
            "Sorry I am not part of the group 😱. Contact your admin to add me to the group"
          );
        }
        // if (!groupId.equals(service.defaultGroupId)) {
        //   GroupAgent groupAgent = (GroupAgent) Context
        //     .get()
        //     .fetchAgent(groupId);
        //   checkGroupMembershipByEmail(email, groupAgent);
        // }
      }

      chatResponseText += "\n";

      Object resp =
        this.getSuccessModelsForGroupAndService(groupId, serviceName)
          .getEntity();
      if (resp instanceof ErrorDTO) {
        throw new ChatException(((ErrorDTO) resp).message);
      }
      SuccessModelDTO success = (SuccessModelDTO) resp;
      boolean measuresOnly;
      if ("getSuccessModel".equals(requestObject.getAsString("intent"))) {
        // if getSuccessModel is recognized as intent, then we inlcude dimensions and
        // factors in the list
        measuresOnly = false;
      } else {
        // chatResponse.put("closeContext", false);
        measuresOnly = true;
      }

      chatResponse.put("closeContext", true);

      chatResponseText +=
        TextFormatter.SuccessModelToText(success.xml, dimension, measuresOnly);

      chatResponse.put("text", chatResponseText);
    } catch (ChatException e) {
      e.printStackTrace();
      chatResponse.put("text", e.getMessage());
      chatResponse.put("closeContext", false);
    } catch (Exception e) {
      e.printStackTrace();
      chatResponse.put("text", "Sorry an error occured 💁");
      chatResponse.put("closeContext", false);
    }
    return Response.ok(chatResponse).build();
  }

  /**
   * Bot function to get a visualization
   *
   * @param body jsonString containing the query, the Chart type and other
   *             optional parameters
   * @return image to be displayed in chat
   */
  @Path("/visualize")
  @POST
  public Response visualizeRequest(String body) {
    // System.out.println("User requesting a visualization");
    // System.out.println("Message body: " + body);
    JSONParser parser = new JSONParser(JSONParser.MODE_PERMISSIVE);
    Response res = null;
    net.minidev.json.JSONObject chatResponse = new net.minidev.json.JSONObject();
    Element desiredMeasure = null;

    try {
      net.minidev.json.JSONObject json = (net.minidev.json.JSONObject) parser.parse(
        body
      );
      String email = json.getAsString("email");
      String channel_id = json.getAsString("channel");
      String measureName = json.getAsString("msg");
      // String intent = json.getAsString("intent");

      net.minidev.json.JSONObject context = userContext.get(email);
      if (context == null) context = new net.minidev.json.JSONObject();

      String groupName = json.getAsString("groupName");
      if (groupName == null) defaultGroupMap.get(channel_id);

      String groupId = this.getGroupIdByName(groupName);
      if (groupId == null) groupId = service.defaultGroupId;

      // String tag = json.getAsString("tag"); //might be usefull in the future to
      // search for measures by tag

      // if (!service.defaultGroupId.equals(groupId)) {
      //   // groups other than the default group need permission to be accessed
      //   GroupAgent groupAgent = (GroupAgent) Context.get().fetchAgent(groupId);
      //   checkGroupMembershipByEmail(email, groupAgent);
      // }

      Document xml = getMeasureCatalogForGroup(groupId, parser);
      desiredMeasure =
        XMLTools.extractElementByName(measureName, xml, "measure");

      // TODO add capability to select measure from list by pproviding number
      // if (
      // intent.equals("number_selection") &&
      // context.get("currentSelection") != null
      // ) {
      // // user selected an item from a the list
      // if (context.get("currentSelection") instanceof List<?>) {
      // List<Node> measures = (List<Node>) context.get("currentSelection");

      // int userSelection = json.getAsNumber("number").intValue() - 1; // user list
      // starts at 1
      // if (measures.size() > userSelection) {
      // desiredMeasure = (Element) measures.toArray()[userSelection];
      // context.remove("currentSelection");
      // }
      // }
      // }

      if (desiredMeasure == null) { // try to find measure using tag search
        List<Node> list = XMLTools.findMeasuresByAttribute(
          xml,
          measureName,
          "tag"
        );
        if (list.isEmpty()) {
          throw new ChatException(
            "No nodes found matching your input💁\n " +
            "you can add them yourself by following this link:\n" +
            "https://sbf-dev.tech4comp.dbis.rwth-aachen.de/monitor/ \n " +
            "or create a requirement by following this link: \n" +
            "https://requirements-bazaar.org/"
          );
        }
        if (list.size() == 1) { // only one result->use this as the desired measure
          desiredMeasure = (Element) list.iterator().next();
        } else {
          context.put("currentSelection", list);
          userContext.put(email, context); // save the current selection in context
          String respString =
            "I found the following measures, matching \"" +
            measureName +
            "\":\n";
          Iterator<Node> it = list.iterator();

          for (int j = 0; it.hasNext(); j++) {
            respString +=
              (j + 1) +
              ". " +
              ((Element) it.next()).getAttribute("name") +
              "\n";
          }
          respString += "Please specify your measure";
          throw new ChatException(respString);
        }
      }

      Element visualization = (Element) desiredMeasure
        .getElementsByTagName("visualization")
        .item(0);

      if (visualization == null) {
        throw new ChatException(
          "The measure is not formed correctly...\nIt does not include a visualization"
        );
      }
      switch (visualization.getAttribute("type")) {
        case "Chart":
          String imagebase64 = getChartFromMeasure(
            desiredMeasure,
            parser,
            visualization
          );
          // chatResponse.put("fileName", "chart.png");
          // chatResponse.put("fileType", "image/png");
          chatResponse.put("fileBody", imagebase64);
          chatResponse.put("fileName", "chart");
          chatResponse.put("fileType", "png");
          res = Response.ok(chatResponse.toString()).build();
          break;
        case "KPI":
          String kpi = getKPIFromMeasure(desiredMeasure, parser, visualization);
          chatResponse.put("text", kpi);
          res = Response.ok(chatResponse.toString()).build();
          break;
        case "Value":
          String value = getValueFromMeasure(
            desiredMeasure,
            parser,
            visualization
          );
          chatResponse.put("text", value);
          res = Response.ok(chatResponse.toString()).build();
          break;
        default:
          throw new IllegalArgumentException(
            "Visualization of type " + visualization.getAttribute("type")
          );
      }
      userContext.remove("email");
    } catch (ChatException e) {
      e.printStackTrace();
      chatResponse.put("text", e.getMessage());
      res = Response.ok(chatResponse.toString()).build();
    } catch (Exception e) {
      e.printStackTrace();
      chatResponse.put("text", "An error occured 😦");
      res = Response.ok(chatResponse.toString()).build();
    }
    return res;
  }

  @Path("/updateSuccessModel")
  @POST
  public Response updateSuccessModel(String body) {
    System.out.println("User requesting an update of the success model");
    Document catalog;
    Document model;
    JSONParser parser = new JSONParser(JSONParser.MODE_PERMISSIVE);
    String groupName = null;
    Response res = null;
    net.minidev.json.JSONObject chatResponse = new net.minidev.json.JSONObject();

    try {
      net.minidev.json.JSONObject json = (net.minidev.json.JSONObject) parser.parse(
        body
      );
      String channel_id = json.getAsString("channel");
      String email = json.getAsString("email");
      String intent = json.getAsString("intent");
      groupName = json.getAsString("groupName");
      if (groupName == null) defaultGroupMap.get(channel_id);
      String groupId = this.getGroupIdByName(groupName);
      if (groupId == null) groupId = service.defaultGroupId;

      if ("startUpdatingModel".equals(intent)) {
        // if user starts the routine we make sure that the context is reset
        userContext.remove(email);
      }
      net.minidev.json.JSONObject context = userContext.get(email);
      if (context == null) {
        context = new net.minidev.json.JSONObject();
      }
      System.out.println("context from the last call: " + context);
      net.minidev.json.JSONObject newContext = getNewContext(context, json);
      Integer userSelection = null;
      String msg = json.getAsString("msg");

      String serviceName = newContext.getAsString("serviceName");
      String dimensionName = newContext.getAsString("dimensionName");
      String factorName = newContext.getAsString("factorName");
      String measureName = newContext.getAsString("measureName");

      if (serviceName == null) serviceName = service.defaultServiceName;

      // if (!service.defaultGroupId.equals(groupId)) {
      //   GroupAgent groupAgent = (GroupAgent) Context.get().fetchAgent(groupId);
      //   checkGroupMembershipByEmail(email, groupAgent);
      // }

      if (msg.length() > 4 && !"number_selection".equals(intent)) { // assume user typed name instead of number
        if ("provideDimension".equals(context.getAsString("intent"))) {
          intent = "provideFactor";
          factorName = msg;
        }
        if ("provideFactor".equals(context.getAsString("intent"))) {
          intent = "provideMeasure";
          measureName = msg;
        }
      }

      if (intent.equals("number_selection")) {
        intent = determineNewIntent(context); // in this case figure out at which step we are from the old context
        newContext.put("intent", intent); // set the newly determined intent in the context

        System.out.println("Intent is now: " + intent);

        userSelection = json.getAsNumber("number").intValue() - 1; // lists in chat start at 1
        Object currentSelection = context.get("currentSelection");
        if (currentSelection == null) {
          throw new Exception("Current selection empty");
        }

        if (currentSelection instanceof NodeList) { // measures and factors are NodeLists
          if (((NodeList) currentSelection).getLength() > userSelection) msg =
            (
              (Element) ((NodeList) currentSelection).item(userSelection)
            ).getAttribute("name");
        } else if (currentSelection instanceof List<?>) { // dimensions are Lists of String names
          if (((List<?>) currentSelection).size() > userSelection) msg =
            (String) ((List<?>) currentSelection).get(userSelection);
        } else {
          throw new ChatException("Something went wrong");
        }

        System.out.println("Resulting input: " + msg);
      }

      userContext.put(email, newContext); // better be safe than sorry...

      switch (intent) {
        case "quit":
          chatResponse.put("text", "Alright, discarding changes...");
          userContext.remove(email); // reset context on quit
          chatResponse.put("closeContext", true);
          break;
        case "startUpdatingModel":
          newContext.put("currentSelection", successDimensions);
          System.out.println("Context is now: " + newContext);
          userContext.put(email, newContext);
          String response =
            "You chose to update the success model for the _" +
            serviceName +
            "_ service and the _" +
            groupName +
            "_ group.\n" +
            "I will now guide you through the updating ✏️ process \n" +
            "Which of the following dimensions do you want to edit?\n\n" +
            TextFormatter.formatSuccessDimensions(successDimensions) +
            "\nChoose one by providing a *number*. You can exit the update process by typing _quit_ at any time.";
          chatResponse.put("text", response);
          chatResponse.put("closeContext", false);
          break;
        case "provideDimension":
          if (msg == null) {
            throw new ChatException("Please provide a dimension");
          }

          System.out.println("User selected the " + msg + " dimension");
          newContext.put("dimensionName", msg); // save the dimensionname

          model =
            getSuccessModelForGroupAndService(groupId, serviceName, parser);
          Element dimension = XMLTools.extractElementByName(
            msg,
            model,
            "dimension"
          );
          if (dimension == null) {
            throw new ChatException(
              "The desired dimension was not found in the success model"
            );
          }
          NodeList factors = dimension.getElementsByTagName("factor");
          newContext.put("currentSelection", factors);
          userContext.put(email, newContext);
          if (factors == null || factors.getLength() == 0) {
            chatResponse.put(
              "text",
              "There are no factors for this dimension yet. \nYou can add one by providing a name."
            );
          } else {
            chatResponse.put(
              "text",
              "Which of the following factors do you want to add a measure to?\n" +
              TextFormatter.formatSuccesFactors(factors) +
              "Choose one by providing a *number*. You can also add a factor by providing a name."
            );
          }

          chatResponse.put("closeContext", false);
          break;
        case "provideFactor":
          if (msg == null) {
            throw new ChatException("Please provide a factor");
          }

          System.out.println("User selected the " + msg + " factor");
          catalog = getMeasureCatalogForGroup(groupId, parser);
          newContext.put("factorName", msg); // save the factorname in context
          NodeList measures = catalog.getElementsByTagName("measure");
          newContext.put("currentSelection", measures);
          userContext.put(email, newContext);
          chatResponse.put(
            "text",
            "Here are the measures defined by the community.\n\n" +
            TextFormatter.formatMeasures(measures) +
            "\nPlease select one of the following measures by choosing a *number* to add it to the factor\n"
          );
          chatResponse.put("closeContext", false);
          break;
        case "provideMeasure":
          if (msg == null) {
            throw new ChatException("Please provide a measure");
          }

          System.out.println("Dimension is: " + dimensionName);
          System.out.println("Factor is: " + factorName);
          System.out.println("Measure is: " + msg);

          catalog = getMeasureCatalogForGroup(groupId, parser);
          model =
            getSuccessModelForGroupAndService(groupId, serviceName, parser);

          Element measureElement = XMLTools.extractElementByName(
            msg,
            catalog,
            "measure"
          );
          Element factorElement = XMLTools.extractElementByName(
            factorName,
            model,
            "factor"
          );

          if (measureElement == null) {
            throw new ChatException("The measure was not found in the catalog");
          }

          if (factorElement == null) {
            System.out.println(
              "Adding new factor " +
              factorName +
              ", because it did not exist before"
            );
            Element dimensionElement = XMLTools.extractElementByName(
              dimensionName,
              model,
              "dimension"
            );
            System.out.println("Create new factor");
            factorElement = model.createElement("factor");
            System.out.println("Setting the factorname");
            factorElement.setAttribute("name", factorName);
            System.out.println("Appending it to the dimension");
            dimensionElement.appendChild(factorElement);
          }

          System.out.println("Appending the measure to the factor");
          Element importNode = (Element) model.importNode(
            measureElement,
            false
          );
          factorElement.appendChild(importNode);
          if (saveModel(model, groupId, serviceName)) {
            chatResponse.put(
              "text",
              "Your measure was successfully added to the model. Here is the _new_ success model:\n" +
              TextFormatter.SuccessModelToText(model)
            );
            userContext.remove(email);
          }
          break;
        case "remove":
          String toBeRemoved = "";

          if (measureName != null || factorName != null) {
            model =
              getSuccessModelForGroupAndService(groupId, serviceName, parser);
            if (measureName != null) {
              toBeRemoved = measureName;
              measureElement =
                XMLTools.extractElementByName(measureName, model, "measure");
              measureElement.getParentNode().removeChild(measureElement);
            } else if (factorName != null) {
              toBeRemoved = factorName;
              factorElement =
                XMLTools.extractElementByName(factorName, model, "factor");
              factorElement.getParentNode().removeChild(factorElement);
            }
            if (saveModel(model, groupId, serviceName)) {
              chatResponse.put(
                "text",
                "\"" +
                toBeRemoved +
                "\"  was successfully removed from the model.\n" +
                "Here is the resulting model:\n" +
                TextFormatter.SuccessModelToText(model)
              );
              userContext.remove(email);
            }
          }

          break;
        // default:
        // System.out.println(
        // "Unexpected intent " +
        // intent +
        // " recognized. Choosing default response"
        // );
        // chatResponse.put("text", formatSuccessDimensions(newContext));
        // break;
      }
    } catch (ChatException e) {
      e.printStackTrace();
      chatResponse.put("text", e.getMessage());
    } catch (ForbiddenException e) {
      e.printStackTrace();
      chatResponse.put(
        "text",
        "Sorry I am not part of the group " +
        groupName +
        "😱. Contact your admin to add me to the group"
      );
    } catch (Exception e) {
      e.printStackTrace();
      chatResponse.put("text", "An error occured 😦");
    }
    res = Response.ok(chatResponse.toString()).build();
    return res;
  }

  @Path("/setGroup")
  @POST
  public Response setDefaultGroup(String body) {
    JSONParser parser = new JSONParser(JSONParser.MODE_PERMISSIVE);

    net.minidev.json.JSONObject chatResponse = new net.minidev.json.JSONObject();
    try {
      net.minidev.json.JSONObject json = (net.minidev.json.JSONObject) parser.parse(
        body
      );
      String intent = json.getAsString("intent");
      if ("quit".equals(intent)) {
        chatResponse.put("text", "Alright.");
        chatResponse.put("closeContext", true);
        return Response.ok(chatResponse).build();
      }

      String groupName = json.getAsString("groupName");
      String email = json.getAsString("email");
      net.minidev.json.JSONObject context = (userContext.get(email));
      String lastIntent = context.getAsString("intent");
      if (groupName == null && "setGroup".equals(lastIntent)) {
        //assume user tried to set default group before this call
        groupName = context.getAsString("msg");
      }
      context.put("intent", intent);
      userContext.put(email, context);

      if (groupName == null) {
        throw new ChatException("Please provide a groupName", false);
      }
      String channel_id = json.getAsString("channel");
      defaultGroupMap.put(channel_id, groupName);
      chatResponse.put("text", "Consider it done.😉");
    } catch (ChatException e) {
      chatResponse.appendField("text", e.getMessage());
      chatResponse.put("closeContext", e.getCloseContext());
    } catch (Exception e) {
      e.printStackTrace();
      chatResponse.appendField("text", "");
    }

    return Response.ok(chatResponse).build();
  }

  private boolean saveModel(Document model, String groupId, String serviceName)
    throws ChatException, ForbiddenException {
    SuccessModelDTO successModel = new SuccessModelDTO();
    System.out.println("Transforming model into xml string");
    successModel.xml = XMLTools.toXMLString(model);
    System.out.println("Updating the success model");
    try {
      Response response = updateSuccessModelsForGroupAndService(
        groupId,
        serviceName,
        successModel
      );
      // newContext.put("newModel", model);
      if (response.getStatus() == 200) {
        return true;
      } else {
        throw new ChatException("The model could not be updated 😦");
      }
    } catch (MalformedXMLException | FileBackendException e) {
      throw new ChatException("Something went wrong while saving the model 🙇");
    }
  }

  private void checkGroupMembershipByEmail(String email, GroupAgent groupAgent)
    throws ChatException, AgentOperationFailedException {
    try {
      String agentId = Context.get().getUserAgentIdentifierByEmail(email);

      if (!groupAgent.hasMember(agentId)) {
        throw new ChatException(
          "You are not a part of the group 😅. Contact your admin to be added to the group"
        );
      }
    } catch (AgentNotFoundException e) {
      e.printStackTrace();
      throw new ChatException(
        "Your email ✉️ is not registered in the las2peer network. \nContact your admin or signin to a laspeer service in the network"
      );
    }
  }

  private net.minidev.json.JSONObject getNewContext(
    net.minidev.json.JSONObject context,
    net.minidev.json.JSONObject newinfo
  ) {
    net.minidev.json.JSONObject newContext = new net.minidev.json.JSONObject();
    newContext.putAll(context); // copy the values from the old context
    Set<Entry<String, Object>> entries = newinfo.entrySet();
    for (Entry<String, Object> entry : entries) {
      if (entry.getValue() != null) newContext.put(
        entry.getKey(),
        entry.getValue()
      ); // overwrite or set new
    }
    return newContext;
  }

  /**
   * Determines at which step in the success modeling the user is.
   *
   * @param oldContext context from the last call
   * @return
   */
  private String determineNewIntent(net.minidev.json.JSONObject oldContext) {
    if (
      oldContext == null || oldContext.getAsString("intent") == null
    ) return "startUpdatingModel";

    String newIntent = "startUpdatingModel"; // first and default step
    String oldIntent = oldContext.getAsString("intent");

    System.out.println("Determening new intent...");
    System.out.println("Old Intent: " + oldIntent);
    System.out.println("Old Context: " + oldContext.toString());

    if ("startUpdatingModel".equals(oldContext.getAsString("intent"))) {
      newIntent = "provideDimension";
    }

    if (oldContext.containsKey("dimensionName")) {
      newIntent = "provideFactor";
      if (oldContext.containsKey("factorName")) {
        newIntent = "provideMeasure";
      }
    }
    return newIntent;
  }

  private Document getSuccessModelForGroupAndService(
    String groupName,
    String serviceName,
    JSONParser parser
  )
    throws Exception {
    Document model = null;

    Object response = getSuccessModelsForGroupAndService(groupName, serviceName)
      .getEntity();

    if (!(response instanceof SuccessModelDTO)) {
      System.out.println(response);
      throw new ChatException(
        "I could not get the success catalog for your group 😔"
      );
    }

    String xmlString = ((SuccessModelDTO) response).xml;
    model = XMLTools.loadXMLFromString(xmlString);

    return model;
  }

  private Document getMeasureCatalogForGroup(
    String groupName,
    JSONParser parser
  )
    throws Exception {
    Document catalog = null;

    Object response = getMeasureCatalogForGroup(groupName).getEntity();

    if (!(response instanceof String)) {
      System.out.println(response);

      System.out.println(
        "If you are using the file service, this probably means that the fileservice cannot be found in the network"
      );
      throw new ChatException(
        "I could not get the measure catalog for your group 😔"
      );
    }
    net.minidev.json.JSONObject json = (net.minidev.json.JSONObject) parser.parse(
      (String) response
    );
    String xmlString = ((net.minidev.json.JSONObject) json).getAsString("xml");
    catalog = XMLTools.loadXMLFromString(xmlString);

    return catalog;
  }

  // public Document loadXMLFromString(String xml) throws Exception {
  // DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

  // factory.setNamespaceAware(true);
  // DocumentBuilder builder = factory.newDocumentBuilder();

  // return builder.parse(new ByteArrayInputStream(xml.getBytes()));
  // }

  /**
   * Makes a request to the GraphQl service
   *
   * @param json contains dbName: name if the db, dbSchema: name of the db schema
   *             and query sql query
   * @return the requested data
   * @throws ChatException
   */
  private InputStream graphQLQuery(net.minidev.json.JSONObject json)
    throws Exception {
    String dbName = json.getAsString("dbName");
    String dbSchema = json.getAsString("dbSchema");
    String query = json.getAsString("query");

    String protocol = service.GRAPHQL_PROTOCOL + "//";

    try {
      String queryString = prepareGQLQueryString(dbName, dbSchema, query);

      String urlString =
        protocol + service.GRAPHQL_HOST + "/graphql?query=" + queryString;

      URL url = new URL(urlString);
      HttpURLConnection con = (HttpURLConnection) url.openConnection();

      return con.getInputStream();
    } catch (IOException e) {
      e.printStackTrace();
      throw new ChatException("Sorry the graphQL request has failed 😶");
    }
  }

  /**
   * Makes a request to the GraphQl service, uses default database and schema
   *
   * @param query sql query
   * @return the requested data
   * @throws ChatException
   */
  private InputStream graphQLQuery(String query) throws ChatException {
    try {
      String queryString = prepareGQLQueryString(query);
      URL url = new URI(
        service.GRAPHQL_PROTOCOL,
        service.GRAPHQL_HOST,
        "/graphql/graphql",
        "query=" + queryString,
        null
      )
        .toURL();
      System.out.println("Graphql request: " + url);
      HttpURLConnection con = (HttpURLConnection) url.openConnection();

      return con.getInputStream();
    } catch (IOException | URISyntaxException e) {
      e.printStackTrace();
      throw new ChatException("Sorry the graphQL request has failed 😶");
    }
  }

  /**
   * Makes a request to the GraphQl service
   *
   * @param query    sql query
   * @param dbName   name of the database as defined when adding the database to
   *                 graphql api
   * @param dbSchema name of the database schema as defined when adding the
   *                 database to graphql api
   * @return response from graphql api
   * @throws ChatException
   */
  private InputStream graphQLQuery(
    String query,
    String dbName,
    String dbSchema
  )
    throws Exception {
    try {
      String queryString = prepareGQLQueryString(dbName, dbSchema, query);
      URL url = new URI(
        service.GRAPHQL_PROTOCOL,
        service.GRAPHQL_HOST,
        "/graphql/graphql",
        "query=" + queryString,
        null
      )
        .toURL();
      System.out.println("Graphql request: " + url);
      HttpURLConnection con = (HttpURLConnection) url.openConnection();

      return con.getInputStream();
    } catch (IOException  e) {
      e.printStackTrace();
      throw new ChatException("Sorry the graphQL request has failed 😶");
    }
     catch (URISyntaxException  e) {
      e.printStackTrace();
      throw new ChatException("Sorry, I could not encode the query 😶");
    }
  }

  /**
   * Prepares the string to the customQuery query of the graphql schema
   *
   * @param dbName   name of the database. This name uniquelly identifies the
   *                 datase on the graphql service
   * @param dbSchema name of the database schema
   * @param query    query which can be used as the query parameter in the graphql
   *                 http request
   * @return
   * @throws ChatException
   */
  private String prepareGQLQueryString(
    String dbName,
    String dbSchema,
    String query
  )
    throws Exception {
    if (dbSchema == null || dbSchema.trim().isEmpty()) {
      dbSchema = this.defaultDatabaseSchema;
    }
    if (dbName == null || dbName.trim().isEmpty()) {
      dbName = this.defaultDatabase;
    }
    if (query == null) {
      throw new Exception("Query cannot be null");
    }

    System.out.println("SQL query untouched: "+ query);
    query = query.trim();
    query = query.replace("\n", " ");
    query = query.replace("\"", "\\\"");
   
    System.out.println("SQL query: "+ query);
    String test = String.format("{customQuery(dbName:\"%s\",dbSchema:\"%s\",query:\"%s\")}",dbName,dbSchema,query );
    System.out.println(test);
    // System.out.println(dbName + dbSchema + query);

    return (
      "{customQuery(dbName: \"" +
      dbName +
      "\",dbSchema: \"" +
      dbSchema +
      "\",query: \"" +
      query +
      "\")}"
    );
  }

  // private String prepareGQLQueryString(
  //   String dbName,
  //   String dbSchema,
  //   String query,
  //   String serviceNode,
  //   String serviceAgentId
  // )
  //   throws Exception {
  //   if (dbSchema == null || dbSchema.trim().isEmpty()) {
  //     dbSchema = this.defaultDatabaseSchema;
  //   }
  //   if (dbName == null || dbName.trim().isEmpty()) {
  //     dbName = this.defaultDatabase;
  //   }
  //   if (query == null) {
  //     throw new Exception("Query cannot be null");
  //   }
  //   query = query.replace("\n", " ");
  //   query.replaceAll("SOURCE_NODE\s*=\s*’$NODE$", "SOURCE_NODE=" + serviceNode);
  //   query.replaceAll(
  //     "SOURCE_AGENT\s*=\s*’$AGENT$",
  //     "SOURCE_AGENT=" + serviceAgentId
  //   );
  //   // System.out.println(dbName + dbSchema + query);
  //   return (
  //     "{customQuery(dbName: \"" +
  //     dbName +
  //     "\",dbSchema: \"" +
  //     dbSchema +
  //     "\",query: \"" +
  //     query +
  //     "\")}"
  //   );
  // }

  /**
   * Prepares the string to the customQuery query of the graphql schema. Will use
   * the default database and schema
   *
   * @param query sql query
   * @return query which can be used as the query parameter in the graphql http
   *         request
   * @throws ChatException
   */
  private String prepareGQLQueryString(String query)
    throws UnsupportedEncodingException {
    if (query.contains("\"")) {
      query = java.net.URLEncoder.encode(query.replaceAll("\"", "'"), "UTF-8");
    }

    System.out.println("SQL: " + query);
    return (
      "{customQuery(dbName: \"" +
      defaultDatabase +
      "\",dbSchema: \"" +
      defaultDatabaseSchema +
      "\",query: \"" +
      query +
      "\")}"
    );
  }

  /**
   * Makes a call to the visulization service to create a chart as png
   *
   * @param data  Data which should be visualized
   * @param type  type of (Google Charts) chart
   * @param title title of the chart
   * @return chart as base64 encoded string
   * @throws ChatException
   */
  private String getImage(
    net.minidev.json.JSONObject data,
    String type,
    String title
  )
    throws ChatException {
    if (type == null) {
      type = "PieChart";
    }
    data.put("chartType", type);
    if (title != null) {
      JSONObject titleObj = new JSONObject();
      titleObj.put("title", title);
      data.put("options", titleObj.toJSONString());
    }

    try {
      String urlString = service.CHART_API_ENDPOINT + "/customQuery";
      // if (!urlString.contains("http")) {
      // urlString = "http://" + urlString;
      // }
      URL url = new URL(urlString);
      HttpURLConnection con = (HttpURLConnection) url.openConnection();
      con.setRequestProperty("Content-Type", "application/json");
      con.setRequestMethod("POST");
      con.setDoOutput(true);
      DataOutputStream wr = new DataOutputStream(con.getOutputStream());
      wr.writeBytes(data.toJSONString());
      wr.flush();
      wr.close();

      InputStream response = con.getInputStream();

      return toBase64(response);
    } catch (IOException e) {
      e.printStackTrace();
      throw new ChatException("Sorry the visualization has failed 😶");
    }
  }

  /**
   * Transforms an Input stream into a base64 encoded string
   *
   * @param is Input stream of a connection
   * @return base64 encoded string
   */
  private String toBase64(InputStream is) {
    try {
      byte[] bytes = org.apache.commons.io.IOUtils.toByteArray(is);

      String chunky = Base64.getEncoder().encodeToString(bytes);

      return chunky;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Get the chart from a measure
   *
   * @param measure       measure as xml node
   * @param parser        json parser to parse response from api calls
   * @param visualization //the visualization xml node
   * @return chart as base64 encoded string
   * @throws Exception
   */
  private String getChartFromMeasure(
    Element measure,
    JSONParser parser,
    Element visualization
  )
    throws Exception {
    String b64 = null;
    String dbName = defaultDatabase;
    String dbSchema = defaultDatabaseSchema;
    String measureName = "";
    net.minidev.json.JSONObject json = null;
    InputStream graphQLResponse = null;

    NodeList datasets = measure.getElementsByTagName("data");
    if (datasets.getLength() > 0) {
      NodeList queries =
        ((Element) datasets.item(0)).getElementsByTagName("query");
      Element database = XMLTools.extractElementByTagName(measure, "database");
      if (database != null) {
        dbName = database.getAttribute("name");
        dbSchema = database.getAttribute("dbSchema");
      }
      measureName = measure.getAttribute("name");
      String query = ((Element) queries.item(0)).getTextContent();
      graphQLResponse = graphQLQuery(query, dbName, dbSchema);
    } else {
      NodeList queries = measure.getElementsByTagName("query");
      Element database = XMLTools.extractElementByTagName(measure, "database");
      if (database != null) {
        dbName = database.getAttribute("name");
        dbSchema = database.getAttribute("dbSchema");
      }
      measureName = measure.getAttribute("name");
      String query = ((Element) queries.item(0)).getTextContent();
      graphQLResponse = graphQLQuery(query, dbName, dbSchema);
    }

    json = (net.minidev.json.JSONObject) parser.parse(graphQLResponse);
    System.out.println("gql response: " + json);

    if (json.get("customQuery") == null) {
      throw new ChatException(
        "No data has been collected for this measure yet"
      );
    }

    String chartType = visualization
      .getElementsByTagName("chartType")
      .item(0)
      .getTextContent();
    String chartTitle = measureName;

    b64 = getImage(json, chartType, chartTitle);
    return b64;
  }

  /**
   * Visualizes a KPI from a measure
   *
   * @param measure measure as xml node
   * @param parser  json parser to parse response from api calls
   * @return
   * @throws Exception
   */
  private String getKPIFromMeasure(
    Element measure,
    JSONParser parser,
    Element visualization
  )
    throws Exception {
    String kpi = "";
    String dbName = defaultDatabase;
    String dbSchema = defaultDatabaseSchema;

    String measureName = measure.getAttribute("name");
    NodeList queries = measure.getElementsByTagName("query");
    Element database = XMLTools.extractElementByTagName(measure, "database");
    if (database != null) {
      dbName = database.getAttribute("name");
      dbSchema = database.getAttribute("dbSchema");
    }

    kpi += measureName + ": \n";

    HashMap<Integer, String> operationInfo = new HashMap<Integer, String>(); // holds the childs of visualization
    for (int i = 0; i < visualization.getChildNodes().getLength(); i++) {
      Node node = visualization.getChildNodes().item(i);
      if (node instanceof Element) {
        int index = Integer.parseInt(((Element) node).getAttribute("index"));
        String name = ((Element) node).getAttribute("name");
        kpi += name;
        operationInfo.put(index, name); // operands might not be sorted by index}
      }
    }
    kpi += "=";

    HashMap<String, Number> values = new HashMap<String, Number>();
    for (int i = 0; i < queries.getLength(); i++) {
      String queryName = ((Element) queries.item(i)).getAttribute("name");
      String sqlQueryString = java.net.URLEncoder.encode(
        ((Element) queries.item(i)).getTextContent().replaceAll("\"", "'"),
        "UTF-8"
      );

      System.out.println(sqlQueryString);
      InputStream graphQLResponse = graphQLQuery(
        sqlQueryString,
        dbName,
        dbSchema
      );
      net.minidev.json.JSONObject json = (net.minidev.json.JSONObject) parser.parse(
        graphQLResponse
      );
      String value = null;

      value = extractValue(json, parser);
      values.put(queryName, Float.valueOf(value)); // save as floats idk
    }

    float accu = 0; // saves the result
    float curr = 0; // current value which accu will be operated on
    for (int i = 0; i < operationInfo.size(); i++) {
      if (i == 0) {
        accu = (Float) values.get(operationInfo.get(i));
      } else if (i % 2 == 1) {
        curr = (Float) values.get(operationInfo.get(i + 1));
        switch (operationInfo.get(i)) {
          case "/":
            if (
              curr == 0
            ) return "You are trying to divide something by 0 😅"; else accu =
              accu / curr;
            break;
          case "*":
            accu = accu * curr;
            break;
          case "-":
            accu = accu - curr;
            break;
          case "+":
            accu = accu + curr;
            break;
        }
      }
    }
    kpi += String.valueOf(accu);
    return kpi;
  }

  /**
   * Returns the value from a measure
   *
   * @param measure measure as xml node
   * @param parser  json parser to parse response from api calls
   * @return
   * @throws Exception
   */
  private String getValueFromMeasure(
    Element measure,
    JSONParser parser,
    Element visualization
  )
    throws Exception {
    String value = null;
    InputStream graphQLResponse = null;
    String dbName = defaultDatabase;
    String dbSchema = defaultDatabaseSchema;

    String measureName = measure.getAttribute("name");
    NodeList units = visualization.getElementsByTagName("unit");
    String unit = units.getLength() > 0 ? units.item(0).getTextContent() : null;

    NodeList datasets = measure.getElementsByTagName("data");
    if (datasets.getLength() > 0) {
      NodeList queries =
        ((Element) datasets.item(0)).getElementsByTagName("query");
      Element database = XMLTools.extractElementByTagName(measure, "database");
      if (database != null) {
        dbName = database.getAttribute("name");
        dbSchema = database.getAttribute("dbSchema");
      }
      measureName = measure.getAttribute("name");
      String query = ((Element) queries.item(0)).getTextContent();
      graphQLResponse = graphQLQuery(query, dbName, dbSchema);
    } else {
      NodeList queries = measure.getElementsByTagName("query");
      Element database = XMLTools.extractElementByTagName(measure, "database");
      if (database != null) {
        dbName = database.getAttribute("name");
        dbSchema = database.getAttribute("dbSchema");
      }
      measureName = measure.getAttribute("name");
      String query = ((Element) queries.item(0)).getTextContent();
      graphQLResponse = graphQLQuery(query, dbName, dbSchema);
    }

    net.minidev.json.JSONObject json = (net.minidev.json.JSONObject) parser.parse(
      graphQLResponse
    );
    value = extractValue(json, parser);
    return unit != null
      ? measureName + ": " + value + unit
      : measureName + ": " + value;
  }

  /**
   * Extracts a single value from the graphql response
   *
   * @param jsonObject contains the desired data under customQuery
   * @param p          used to parse the data
   * @return
   */
  private String extractValue(
    net.minidev.json.JSONObject jsonObject,
    JSONParser p
  )
    throws ChatException {
    JSONArray jsonArray = null;
    System.out.println(jsonObject);
    if (jsonObject.get("customQuery") instanceof String) {
      String result = (String) jsonObject.get("customQuery");
      System.out.println(result);
      try {
        jsonArray =
          (JSONArray) ((net.minidev.json.JSONObject) p.parse(result)).get(
              "result"
            );
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      jsonArray = (JSONArray) jsonObject.get("customQuery");
    }
    if (jsonArray == null) {
      throw new ChatException("No data has been collected for this measure");
    }
    Object[] values =
      ((net.minidev.json.JSONObject) jsonArray.get(0)).values().toArray();
    if (values.length == 0) {
      throw new ChatException("No data has been collected for this measure");
    }
    return values[0].toString();
  }

  /** Exceptions ,with messages, that should be returned in Chat */
  protected static class ChatException extends Exception {

    private static final long serialVersionUID = 1L;
    private final boolean closeContext;

    protected boolean getCloseContext() {
      return this.closeContext;
    }

    protected ChatException(String message) {
      super(message);
      this.closeContext = true;
    }

    protected ChatException(String message, boolean closeContext) {
      super(message);
      this.closeContext = closeContext;
    }
  }
}
