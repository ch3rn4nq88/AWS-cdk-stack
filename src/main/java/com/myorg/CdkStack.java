package com.myorg;

import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.apigateway.*;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.*;

public class CdkStack extends Stack {

    public static final List<CfnTag> TAGS= List.of(CfnTag.builder().key("department").value("development").build());

    public CdkStack(final Construct parent, final String id) {
        this(parent, id, null);
    }

    public CdkStack(final Construct parent, final String id, final StackProps props) {
        super(parent, id, props);
/*
       final TableV2 table = TableV2.Builder.create(this, "Table")
                .tableName("productos")
                .tags(List.of(CfnTag.builder().key("department").value("development").build()))
                .removalPolicy(RemovalPolicy.DESTROY)
                .partitionKey(Attribute.builder().name("pk").type(AttributeType.STRING).build())
                .build();
 */
        final RestApi apigateway =
                RestApi.Builder.create(this, "CdkApiGatewayProductsAPI")
                        .description("Products API by Carlos Quintana")
                        .endpointTypes(List.of(EndpointType.REGIONAL))
                        .restApiName("Products")
                        .build();
                Tags.of(apigateway).add("department", "development");
                apigateway.getRoot().addResource("products").addResource("v1");

        final Role role = Role.Builder.create(this, "APIProductsLambdaExecutionRole")
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .managedPolicies(Arrays.asList(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"),
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonDynamoDBFullAccess"))
                )
                .build();

        setRestApiEndpoints(apigateway, generateLambdaFunctions(role));
    }
    private static FunctionProps.Builder getFunctionBuilder(Role role){
        return FunctionProps.builder()
                .role(role)
                .runtime(Runtime.JAVA_17)
                .memorySize(1024)
                .code(Code.fromAsset("../../../.m2/repository/com/desarrolloinnovador/aws/ProductosLambda/1.0-SNAPSHOT/ProductosLambda-1.0-SNAPSHOT.jar" ))
                .timeout(Duration.seconds(10))
                .logRetention(RetentionDays.ONE_DAY);
    }

    private Map<String, Function> generateLambdaFunctions(Role role){


        var functionList=  new HashMap<String, FunctionProps.Builder>();
        functionList.putIfAbsent("CDK-get", getFunctionBuilder(role)
                .functionName("CDK-get")
                .description("Get endpoint")
                .handler("com.desarrolloinnovador.aws.functions.ProductService::getProducts"));

        functionList.putIfAbsent("CDK-deleteById", getFunctionBuilder(role)
                .functionName("CDK-deleteById")
                .description("deleteProductById endpoint")
                .handler("com.desarrolloinnovador.aws.functions.ProductService::deleteProductById"));

        functionList.putIfAbsent("CDK-add", getFunctionBuilder(role)
                .functionName("CDK-add")
                .description("Add endpoint")
                .handler("com.desarrolloinnovador.aws.functions.ProductService::addProduct"));

        functionList.putIfAbsent("CDK-update", getFunctionBuilder(role)
                .handler("com.desarrolloinnovador.aws.functions.ProductService::updateProduct")
                .functionName("CDK-update")
                .description("Update endpoint"));

        var functionMap = new HashMap<String, Function>();

        functionList.forEach((key,functionBuilder)-> {
            Function function = new Function(this, key, functionBuilder.build());
            System.out.println(function.getFunctionName() + "CREATED");
            Tags.of(function).add("department", "development");
            functionMap.put(key,function);
        });

        return functionMap;

    }
    private static void setRestApiEndpoints(RestApi restApi, Map<String, Function> functions){

        Map<String, IModel> map= new HashMap<>();
        map.put("application/json", Model.EMPTY_MODEL);

        Map<String, String> responseTemplate= new HashMap<>();
        responseTemplate.put("application/json", "");

        Map<String, String> requestTemplateUpdate= new HashMap<>();
        requestTemplateUpdate.put("application/json","{ "       +
                                  "    \"productId\": \"$input.params('productId')\"   "  +
                                  "}");

        restApi.getRoot().getResource("products").getResource("v1")
                .addMethod("POST",
                        LambdaIntegration.Builder.create(functions.get("CDK-add"))
                                .integrationResponses(List.of(
                                        IntegrationResponse.builder()
                                                .responseTemplates(responseTemplate)
                                                .statusCode("200").build()))
                                .passthroughBehavior(PassthroughBehavior.WHEN_NO_MATCH)
                                .proxy(false).build())
                .addMethodResponse(MethodResponse.builder()
                        .responseModels(map)
                        .statusCode("200").build()

                );

        restApi.getRoot().getResource("products").getResource("v1")
                .addMethod("GET",
                        LambdaIntegration.Builder.create(functions.get("CDK-get"))
                                .integrationResponses(List.of(
                                        IntegrationResponse.builder().statusCode("200").build()))
                                .passthroughBehavior(PassthroughBehavior.WHEN_NO_MATCH)
                                .proxy(false)
                                .build())
                .addMethodResponse(MethodResponse.builder().statusCode("200").build());

        restApi.getRoot().getResource("products").getResource("v1")
                .addResource("{productId}")
                .addMethod("PUT",
                    LambdaIntegration.Builder.create(functions.get("CDK-update"))
                        .integrationResponses(List.of(
                                IntegrationResponse.builder()
                                        .responseTemplates(responseTemplate)
                                        .statusCode("200").build()))
                        .requestTemplates(requestTemplateUpdate)
                        .passthroughBehavior(PassthroughBehavior.WHEN_NO_MATCH)
                        .proxy(true)
                        .build()
                )
                .addMethodResponse(MethodResponse.builder().statusCode("200").build());


        restApi.getRoot().getResource("products").getResource("v1").getResource("{productId}")
                .addMethod("DELETE",
                LambdaIntegration.Builder.create(functions.get("CDK-deleteById"))
                        .integrationResponses(List.of(
                                IntegrationResponse.builder()
                                        .responseTemplates(responseTemplate)
                                        .statusCode("200").build()))
                        .passthroughBehavior(PassthroughBehavior.WHEN_NO_MATCH)
                        .requestTemplates(requestTemplateUpdate)
                        .proxy(false).build())
                .addMethodResponse(MethodResponse.builder()
                        .responseModels(map)
                        .statusCode("200").build());


    }



}
