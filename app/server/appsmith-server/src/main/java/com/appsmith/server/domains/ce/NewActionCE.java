package com.appsmith.server.domains.ce;

import com.appsmith.external.models.ActionConfiguration;
import com.appsmith.external.models.ActionDTO;
import com.appsmith.external.models.BranchAwareDomain;
import com.appsmith.external.models.Datasource;
import com.appsmith.external.models.Documentation;
import com.appsmith.external.models.PluginType;
import com.appsmith.external.views.Views;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;

@Getter
@Setter
@ToString
@FieldNameConstants
public class NewActionCE extends BranchAwareDomain {

    // Fields in action that are not allowed to change between published and unpublished versions
    @JsonView(Views.Public.class)
    String applicationId;

    @JsonView(Views.Public.class)
    String workspaceId;

    @JsonView(Views.Public.class)
    PluginType pluginType;

    @JsonView(Views.Public.class)
    String pluginId;

    @JsonView(Views.Public.class)
    Documentation documentation; // Documentation for the template using which this action was created

    // Action specific fields that are allowed to change between published and unpublished versions
    @JsonView(Views.Public.class)
    ActionDTO unpublishedAction;

    @JsonView(Views.Public.class)
    ActionDTO publishedAction;

    @Override
    public void sanitiseToExportDBObject() {
        this.setApplicationId(null);
        this.setWorkspaceId(null);
        this.setDocumentation(null);
        ActionDTO unpublishedAction = this.getUnpublishedAction();
        if (unpublishedAction != null) {
            unpublishedAction.sanitiseToExportDBObject();
        }
        ActionDTO publishedAction = this.getPublishedAction();
        if (publishedAction != null) {
            publishedAction.sanitiseToExportDBObject();
        }
        super.sanitiseToExportDBObject();
    }

    public static class Fields extends BranchAwareDomain.Fields {
        public static final String unpublishedAction_datasource_id =
                String.join(".", unpublishedAction, ActionDTO.Fields.datasource, Datasource.Fields.id);
        public static final String unpublishedAction_name = String.join(".", unpublishedAction, ActionDTO.Fields.name);
        public static final String unpublishedAction_pageId =
                String.join(".", unpublishedAction, ActionDTO.Fields.pageId);
        public static final String unpublishedAction_deletedAt =
                String.join(".", unpublishedAction, ActionDTO.Fields.deletedAt);
        public static final String unpublishedAction_contextType =
                String.join(".", unpublishedAction, ActionDTO.Fields.contextType);
        public static final String unpublishedAction_userSetOnLoad =
                String.join(".", unpublishedAction, ActionDTO.Fields.userSetOnLoad);
        public static final String unpublishedAction_executeOnLoad =
                String.join(".", unpublishedAction, ActionDTO.Fields.executeOnLoad);
        public static final String unpublishedAction_fullyQualifiedName =
                String.join(".", unpublishedAction, ActionDTO.Fields.fullyQualifiedName);
        public static final String unpublishedAction_actionConfiguration_httpMethod = String.join(
                ".", unpublishedAction, ActionDTO.Fields.actionConfiguration, ActionConfiguration.Fields.httpMethod);

        public static final String publishedAction_name = String.join(".", unpublishedAction, ActionDTO.Fields.name);
        public static final String publishedAction_pageId =
                String.join(".", unpublishedAction, ActionDTO.Fields.pageId);
        public static final String publishedAction_contextType =
                String.join(".", unpublishedAction, ActionDTO.Fields.contextType);
    }
}
