/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.grpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.core.api.DMNFactory;
import org.kie.dmn.core.compiler.RuntimeTypeCheckOption;
import org.kie.dmn.core.impl.DMNRuntimeImpl;
import org.kie.dmn.core.util.KieHelper;

public class KieGrpcServer {

    private static final Logger logger = Logger.getLogger(KieGrpcServer.class.getName());

    private Server server;

    public static void main(String[] args) throws IOException, InterruptedException {
        final KieGrpcServer server = new KieGrpcServer();
        server.start();
        server.blockUntilShutdown();
    }

    private static DMNRuntime createRuntime(final String fileName) {

        final KieServices kieServices = KieServices.Factory.get();
        final Resource resource = kieServices.getResources().newClassPathResource(fileName, KieGrpcServer.class);
        final ReleaseId releaseId = getReleaseId(kieServices);
        final KieContainer kieContainer = KieHelper.getKieContainer(releaseId, resource);

        return typeSafeGetKieRuntime(kieContainer);
    }

    private static ReleaseId getReleaseId(final KieServices kieServices) {
        return kieServices.newReleaseId("org.kie", "dmn-test-" + UUID.randomUUID(), "1.0");
    }

    private static DMNRuntime typeSafeGetKieRuntime(final KieContainer kieContainer) {
        final DMNRuntime dmnRuntime = kieContainer.newKieSession().getKieRuntime(DMNRuntime.class);
        ((DMNRuntimeImpl) dmnRuntime).setOption(new RuntimeTypeCheckOption(true));
        return dmnRuntime;
    }

    private void start() throws IOException {

        int port = 50051;

        server = ServerBuilder.forPort(port)
                .addService(new DinnerImpl())
                .build()
                .start();
        logger.info("The KIE gRPC Server running on port: " + port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down KIE gRPC Server...");
            KieGrpcServer.this.stop();
            System.out.println("Done.");
        }));
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    static class DinnerImpl extends DinnerGrpc.DinnerImplBase {

        @Override
        public void process(final DinnerInput input,
                            final StreamObserver<DinnerOutput> responseObserver) {

            final DMNRuntime runtime = createRuntime("dinner.dmn");
            final DMNModel dmnModel = runtime.getModel("http://www.trisotech.com/definitions/_0c45df24-0d57-4acc-b296-b4cba8b71a36", "Dinner");

            final DMNResult dmnResult = runtime.evaluateAll(dmnModel, makeDMNContext(input));
            final DinnerOutput build = makeDinnerOutput(dmnResult.getContext().getAll());

            responseObserver.onNext(build);
            responseObserver.onCompleted();
        }
    }

    private static DinnerOutput makeDinnerOutput(final Map<String, Object> result) {

        final DinnerOutput.Builder dinnerBuilder = DinnerOutput.newBuilder();
        final String dish = (String) result.get("Dish");
        final ArrayList<String> drinks = (ArrayList<String>) result.get("Drinks");
        final String whereToEat = (String) result.get("Where to eat");

        dinnerBuilder.setDish(dish);
        dinnerBuilder.setWhereToEat(whereToEat);
        dinnerBuilder.addAllDrinks(drinks);

        return dinnerBuilder.build();
    }

    private static DMNContext makeDMNContext(final DinnerInput input) {

        final DMNContext context = DMNFactory.newContext();

        context.set("Guests with children", input.getGuestsWithChildren());
        context.set("Season", input.getSeason());
        context.set("Number of guests", input.getNumberOfGuests());
        context.set("Temp", input.getTemp());
        context.set("Rain Probability", input.getRainProbability());

        return context;
    }
}
