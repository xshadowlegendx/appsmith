package com.appsmith.server.services.ce;

import com.appsmith.external.constants.AnalyticsEvents;
import com.appsmith.external.converters.ISOStringToInstantConverter;
import com.appsmith.server.applications.base.ApplicationService;
import com.appsmith.server.configurations.CloudServicesConfig;
import com.appsmith.server.constants.ArtifactType;
import com.appsmith.server.constants.FieldName;
import com.appsmith.server.domains.Application;
import com.appsmith.server.domains.ApplicationMode;
import com.appsmith.server.domains.User;
import com.appsmith.server.dtos.ApplicationAccessDTO;
import com.appsmith.server.dtos.ApplicationImportDTO;
import com.appsmith.server.dtos.ApplicationJson;
import com.appsmith.server.dtos.ApplicationTemplate;
import com.appsmith.server.dtos.TemplateDTO;
import com.appsmith.server.dtos.TemplateUploadDTO;
import com.appsmith.server.exceptions.AppsmithError;
import com.appsmith.server.exceptions.AppsmithException;
import com.appsmith.server.exports.internal.ExportService;
import com.appsmith.server.helpers.ResponseUtils;
import com.appsmith.server.imports.internal.ImportService;
import com.appsmith.server.services.AnalyticsService;
import com.appsmith.server.services.SessionUserService;
import com.appsmith.server.services.UserDataService;
import com.appsmith.server.solutions.ApplicationPermission;
import com.appsmith.server.solutions.ReleaseNotesService;
import com.appsmith.util.WebClientUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ApplicationTemplateServiceCEImpl implements ApplicationTemplateServiceCE {
    private final CloudServicesConfig cloudServicesConfig;
    private final ReleaseNotesService releaseNotesService;
    private final ImportService importService;
    private final ExportService exportService;
    private final AnalyticsService analyticsService;
    private final UserDataService userDataService;
    private final ApplicationService applicationService;
    private final ResponseUtils responseUtils;
    private final ApplicationPermission applicationPermission;
    private final ObjectMapper objectMapper;
    private final SessionUserService sessionUserService;

    public ApplicationTemplateServiceCEImpl(
            CloudServicesConfig cloudServicesConfig,
            ReleaseNotesService releaseNotesService,
            ImportService importService,
            ExportService exportService,
            AnalyticsService analyticsService,
            UserDataService userDataService,
            ApplicationService applicationService,
            ResponseUtils responseUtils,
            ApplicationPermission applicationPermission,
            ObjectMapper objectMapper,
            SessionUserService sessionUserService) {
        this.cloudServicesConfig = cloudServicesConfig;
        this.releaseNotesService = releaseNotesService;
        this.importService = importService;
        this.exportService = exportService;
        this.analyticsService = analyticsService;
        this.userDataService = userDataService;
        this.applicationService = applicationService;
        this.responseUtils = responseUtils;
        this.applicationPermission = applicationPermission;
        this.objectMapper = objectMapper;
        this.sessionUserService = sessionUserService;
    }

    @Override
    public Flux<ApplicationTemplate> getSimilarTemplates(String templateId, MultiValueMap<String, String> params) {
        UriComponents uriComponents = UriComponentsBuilder.fromUriString(cloudServicesConfig.getBaseUrl())
                .pathSegment("api/v1/app-templates", templateId, "similar")
                .queryParams(params)
                .queryParam("version", releaseNotesService.getRunningVersion())
                .build();

        String apiUrl = uriComponents.toUriString();

        return WebClientUtils.create(apiUrl).get().exchangeToFlux(clientResponse -> {
            if (clientResponse.statusCode().equals(HttpStatus.OK)) {
                return clientResponse.bodyToFlux(ApplicationTemplate.class);
            } else if (clientResponse.statusCode().isError()) {
                return Flux.error(
                        new AppsmithException(AppsmithError.CLOUD_SERVICES_ERROR, clientResponse.statusCode()));
            } else {
                return clientResponse.createException().flatMapMany(Flux::error);
            }
        });
    }

    @Override
    public Mono<List<ApplicationTemplate>> getActiveTemplates(List<String> templateIds) {
        final String baseUrl = cloudServicesConfig.getBaseUrl();

        UriComponentsBuilder uriComponentsBuilder =
                UriComponentsBuilder.newInstance().queryParam("version", releaseNotesService.getRunningVersion());

        if (!CollectionUtils.isEmpty(templateIds)) {
            uriComponentsBuilder.queryParam("id", templateIds);
        }

        // uriComponents will build url in format: version=version&id=id1&id=id2&id=id3
        UriComponents uriComponents = uriComponentsBuilder.build();

        return WebClientUtils.create(baseUrl + "/api/v1/app-templates?" + uriComponents.getQuery())
                .get()
                .exchangeToFlux(clientResponse -> {
                    if (clientResponse.statusCode().equals(HttpStatus.OK)) {
                        return clientResponse.bodyToFlux(ApplicationTemplate.class);
                    } else if (clientResponse.statusCode().isError()) {
                        return Flux.error(
                                new AppsmithException(AppsmithError.CLOUD_SERVICES_ERROR, clientResponse.statusCode()));
                    } else {
                        return clientResponse.createException().flatMapMany(Flux::error);
                    }
                })
                .collectList();
    }

    @Override
    public Mono<ApplicationTemplate> getTemplateDetails(String templateId) {
        final String baseUrl = cloudServicesConfig.getBaseUrl();

        return WebClientUtils.create(baseUrl + "/api/v1/app-templates/" + templateId)
                .get()
                .exchangeToMono(clientResponse -> {
                    if (clientResponse.statusCode().equals(HttpStatus.OK)) {
                        return clientResponse.bodyToMono(ApplicationTemplate.class);
                    } else if (clientResponse.statusCode().isError()) {
                        return Mono.error(
                                new AppsmithException(AppsmithError.CLOUD_SERVICES_ERROR, clientResponse.statusCode()));
                    } else {
                        return clientResponse.createException().flatMap(Mono::error);
                    }
                });
    }

    @Override
    public Mono<ApplicationJson> getApplicationJsonFromTemplate(String templateId) {
        final String baseUrl = cloudServicesConfig.getBaseUrl();
        final String templateUrl = baseUrl + "/api/v1/app-templates/" + templateId + "/application";
        /*
         * using a custom url builder factory because default builder always encodes
         * URL.
         * It's expected that the appDataUrl is already encoded, so we don't need to
         * encode that again.
         * Encoding an encoded URL will not work and end up resulting a 404 error
         */
        final int size = 4 * 1024 * 1024; // 4 MB
        final ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(size))
                .build();

        WebClient webClient = WebClientUtils.builder()
                .uriBuilderFactory(new NoEncodingUriBuilderFactory(templateUrl))
                .exchangeStrategies(strategies)
                .build();

        return webClient
                .get()
                .retrieve()
                .bodyToMono(String.class)
                .map(jsonString -> {
                    Gson gson = new GsonBuilder()
                            .registerTypeAdapter(Instant.class, new ISOStringToInstantConverter())
                            .create();
                    Type fileType = new TypeToken<ApplicationJson>() {}.getType();

                    ApplicationJson jsonFile = gson.fromJson(jsonString, fileType);
                    return jsonFile;
                })
                .switchIfEmpty(
                        Mono.error(new AppsmithException(AppsmithError.NO_RESOURCE_FOUND, "template", templateId)));
    }

    @Override
    public Mono<ApplicationImportDTO> importApplicationFromTemplate(String templateId, String workspaceId) {
        return getApplicationJsonFromTemplate(templateId)
                .flatMap(applicationJson -> Mono.zip(
                        importService.importNewArtifactInWorkspaceFromJson(workspaceId, applicationJson),
                        Mono.just(applicationJson.getExportedApplication().getName())))
                .flatMap(tuple -> {
                    Application application = (Application) tuple.getT1();
                    String templateTitle = tuple.getT2();
                    application.setForkedFromTemplateTitle(templateTitle);
                    return applicationService.save(application).thenReturn(application);
                })
                .flatMap(application -> importService.getArtifactImportDTO(
                        application.getWorkspaceId(), application.getId(), application, ArtifactType.APPLICATION))
                .flatMap(importableArtifactDTO -> {
                    ApplicationImportDTO applicationImportDTO = (ApplicationImportDTO) importableArtifactDTO;
                    Application application = applicationImportDTO.getApplication();
                    ApplicationTemplate applicationTemplate = new ApplicationTemplate();
                    applicationTemplate.setId(templateId);
                    final Map<String, Object> eventData = Map.of(
                            FieldName.APP_MODE, ApplicationMode.EDIT.toString(), FieldName.APPLICATION, application);

                    final Map<String, Object> data = Map.of(
                            FieldName.APPLICATION_ID, application.getId(),
                            FieldName.WORKSPACE_ID, application.getWorkspaceId(),
                            FieldName.TEMPLATE_APPLICATION_NAME, application.getName(),
                            FieldName.SOURCE, "Templates page",
                            FieldName.EVENT_DATA, eventData);

                    return analyticsService
                            .sendObjectEvent(AnalyticsEvents.FORK, applicationTemplate, data)
                            .thenReturn(applicationImportDTO);
                });
    }

    @Override
    public Mono<ApplicationTemplate> getFilters() {
        final String baseUrl = cloudServicesConfig.getBaseUrl();

        return WebClientUtils.create(baseUrl + "/api/v1/app-templates/filters")
                .get()
                .exchangeToMono(clientResponse -> {
                    if (clientResponse.statusCode().equals(HttpStatus.OK)) {
                        return clientResponse.bodyToMono(ApplicationTemplate.class);
                    } else if (clientResponse.statusCode().isError()) {
                        return Mono.error(
                                new AppsmithException(AppsmithError.CLOUD_SERVICES_ERROR, clientResponse.statusCode()));
                    } else {
                        return clientResponse.createException().flatMap(Mono::error);
                    }
                });
    }

    public static class NoEncodingUriBuilderFactory extends DefaultUriBuilderFactory {
        public NoEncodingUriBuilderFactory(String baseUriTemplate) {
            super(UriComponentsBuilder.fromHttpUrl(baseUriTemplate));
            super.setEncodingMode(EncodingMode.NONE);
        }
    }

    /**
     * Merge Template API is slow today because it needs to communicate with
     * ImportExport Service, CloudService and/or serialise and de-serialise the
     * application. This process takes time and the client may cancel the request.
     * This leads to the flow getting stopped
     * midway producing corrupted states.
     * We use the synchronous sink to ensure that even though the client may have
     * cancelled the flow, git operations should
     * proceed uninterrupted and whenever the user refreshes the page, we will have
     * the sane state. Synchronous sink does
     * not take subscription cancellations into account. This means that even if the
     * subscriber has cancelled its
     * subscription, the create method still generates its event.
     */
    @Override
    public Mono<ApplicationImportDTO> mergeTemplateWithApplication(
            String templateId,
            String applicationId,
            String organizationId,
            String branchName,
            List<String> pagesToImport) {
        Mono<ApplicationImportDTO> importedApplicationMono = getApplicationJsonFromTemplate(templateId)
                .flatMap(applicationJson -> {
                    if (branchName != null) {
                        return applicationService
                                .findByBranchNameAndDefaultApplicationId(
                                        branchName, applicationId, applicationPermission.getEditPermission())
                                .flatMap(application -> importService.mergeArtifactExchangeJsonWithImportableArtifact(
                                        organizationId,
                                        application.getId(),
                                        branchName,
                                        applicationJson,
                                        pagesToImport))
                                .map(importableArtifact -> (Application) importableArtifact);
                    }
                    return importService
                            .mergeArtifactExchangeJsonWithImportableArtifact(
                                    organizationId, applicationId, branchName, applicationJson, pagesToImport)
                            .map(importableArtifact -> (Application) importableArtifact);
                })
                .flatMap(application -> importService.getArtifactImportDTO(
                        application.getWorkspaceId(), application.getId(), application, ArtifactType.APPLICATION))
                .flatMap(importableArtifactDTO -> {
                    ApplicationImportDTO applicationImportDTO = (ApplicationImportDTO) importableArtifactDTO;
                    responseUtils.updateApplicationWithDefaultResources(applicationImportDTO.getApplication());
                    Application application = applicationImportDTO.getApplication();
                    ApplicationTemplate applicationTemplate = new ApplicationTemplate();
                    applicationTemplate.setId(templateId);
                    final Map<String, Object> eventData = Map.of(
                            FieldName.APP_MODE, ApplicationMode.EDIT.toString(), FieldName.APPLICATION, application);

                    final Map<String, Object> data = Map.of(
                            FieldName.APPLICATION_ID, application.getId(),
                            FieldName.WORKSPACE_ID, application.getWorkspaceId(),
                            FieldName.TEMPLATE_APPLICATION_NAME, application.getName(),
                            FieldName.SOURCE, "Templates page",
                            FieldName.EVENT_DATA, eventData);

                    return analyticsService
                            .sendObjectEvent(AnalyticsEvents.FORK, applicationTemplate, data)
                            .thenReturn(applicationImportDTO);
                });

        return Mono.create(
                sink -> importedApplicationMono.subscribe(sink::success, sink::error, null, sink.currentContext()));
    }

    private TemplateUploadDTO createTemplateUploadDTO(
            String sourceApplicationId, ApplicationJson appJson, TemplateDTO templateDetails) {
        ApplicationTemplate applicationTemplate = new ApplicationTemplate();
        applicationTemplate.setTitle(templateDetails.getTitle());
        applicationTemplate.setExcerpt(templateDetails.getHeadline());
        applicationTemplate.setDescription(templateDetails.getDescription());
        applicationTemplate.setUseCases(templateDetails.getUseCases());
        applicationTemplate.setAuthorEmail(templateDetails.getAuthorEmail());

        TemplateUploadDTO communityTemplate = new TemplateUploadDTO();
        communityTemplate.setAppJson(appJson);
        communityTemplate.setApplicationTemplate(applicationTemplate);
        communityTemplate.getApplicationTemplate().setAppUrl(templateDetails.getAppUrl());
        communityTemplate.setSourceApplicationId(sourceApplicationId);
        return communityTemplate;
    }

    private Mono<ApplicationTemplate> uploadCommunityTemplateToCS(TemplateUploadDTO communityTemplate) {
        String url = cloudServicesConfig.getBaseUrl() + "/api/v1/app-templates/upload-community-template";
        return uploadTemplate(communityTemplate, url);
    }

    @NotNull private Mono<ApplicationTemplate> uploadTemplate(TemplateUploadDTO communityTemplate, String url) {
        String authHeader = "Authorization";
        String payload;
        try {
            // Please don't use the default ObjectMapper.
            // The default mapper is registered with views.public.class and removes few
            // attributes due to this
            // The templates flow has different requirement hence not using the same
            ObjectMapper ow = new ObjectMapper();
            ow.registerModule(new JavaTimeModule());
            ObjectWriter writer = ow.writer().withDefaultPrettyPrinter();
            payload = writer.writeValueAsString(communityTemplate);
        } catch (Exception e) {
            return Mono.error(e);
        }

        return WebClient.create()
                .post()
                .uri(url)
                .header(authHeader, cloudServicesConfig.getTemplateUploadAuthHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToMono(ApplicationTemplate.class)
                .onErrorResume(error -> Mono.error(new AppsmithException(
                        AppsmithError.CLOUD_SERVICES_ERROR, "while publishing template" + error.getMessage())));
    }

    private Mono<Application> updateApplicationFlags(
            String applicationId, String branchId, boolean isCommunityTemplate) {
        return applicationService
                .findById(applicationId, applicationPermission.getEditPermission())
                .flatMap(application -> {
                    application.setForkingEnabled(true);
                    application.setIsCommunityTemplate(isCommunityTemplate);

                    return applicationService.update(applicationId, application, branchId);
                });
    }

    @Override
    public Mono<Application> publishAsCommunityTemplate(TemplateDTO resource) {
        return exportAppAndUpload(resource, true)
                .zipWith(sessionUserService.getCurrentUser())
                .flatMap(tuple -> {
                    Application application = tuple.getT1();
                    User user = tuple.getT2();
                    final Map<String, Object> data = Map.of(
                            FieldName.APPLICATION_ID, application.getId(),
                            FieldName.WORKSPACE_ID, application.getWorkspaceId());
                    return analyticsService
                            .sendEvent(
                                    AnalyticsEvents.COMMUNITY_TEMPLATE_PUBLISHED.getEventName(),
                                    user.getUsername(),
                                    data)
                            .thenReturn(application);
                });
    }

    @NotNull private Mono<Application> exportAppAndUpload(TemplateDTO resource, boolean isCommunityTemplate) {
        return exportService
                .exportByArtifactIdAndBranchName(
                        resource.getApplicationId(), resource.getBranchName(), ArtifactType.APPLICATION)
                .map(artifactExchangeJson -> (ApplicationJson) artifactExchangeJson)
                .flatMap(appJson -> {
                    TemplateUploadDTO communityTemplate =
                            createTemplateUploadDTO(resource.getApplicationId(), appJson, resource);
                    if (isCommunityTemplate) {
                        return uploadCommunityTemplateToCS(communityTemplate);
                    } else {
                        return uploadAppsmithTemplateToCS(communityTemplate);
                    }
                })
                .then(updateApplicationFlags(
                        resource.getApplicationId(), resource.getBranchName(), isCommunityTemplate))
                .flatMap(application -> {
                    ApplicationAccessDTO applicationAccessDTO = new ApplicationAccessDTO();
                    applicationAccessDTO.setPublicAccess(true);
                    return applicationService.changeViewAccess(
                            application.getId(), resource.getBranchName(), applicationAccessDTO);
                });
    }

    private Mono<ApplicationTemplate> uploadAppsmithTemplateToCS(TemplateUploadDTO communityTemplate) {
        String url = cloudServicesConfig.getBaseUrl() + "/api/v1/app-templates/upload/use-case";
        return uploadTemplate(communityTemplate, url);
    }

    @Override
    public Mono<Boolean> publishAppsmithTemplate(TemplateDTO resource) {
        return exportAppAndUpload(resource, false).thenReturn(Boolean.TRUE);
    }
}
