package com.freightfox.dispatch.api;

import com.freightfox.dispatch.repository.DeliveryOrderRepository;
import com.freightfox.dispatch.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DispatchApiIntegrationTest {

    private static final String ORDERS = """
            {"orders":[
              {"orderId":"ORD001","latitude":12.9716,"longitude":77.5946,"address":"MG Road, Bangalore, Karnataka, India","packageWeight":10,"priority":"HIGH"},
              {"orderId":"ORD002","latitude":13.0827,"longitude":80.2707,"address":"Anna Salai, Chennai, Tamil Nadu, India","packageWeight":20,"priority":"MEDIUM"}
            ]}""";

    private static final String VEHICLES = """
            {"vehicles":[
              {"vehicleId":"VEH001","capacity":100,"currentLatitude":12.9716,"currentLongitude":77.6413,"currentAddress":"Indiranagar, Bangalore, Karnataka, India"},
              {"vehicleId":"VEH002","capacity":150,"currentLatitude":13.0674,"currentLongitude":80.2376,"currentAddress":"T Nagar, Chennai, Tamil Nadu, India"}
            ]}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DeliveryOrderRepository orderRepository;
    @Autowired
    private VehicleRepository vehicleRepository;

    @BeforeEach
    void clean() {
        orderRepository.deleteAll();
        vehicleRepository.deleteAll();
    }

    private void postJson(String url, String body) throws Exception {
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    @Test
    void acceptsOrdersAndVehiclesAndReturnsExpectedMessages() throws Exception {
        mvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content(ORDERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Delivery orders accepted."))
                .andExpect(jsonPath("$.status").value("success"));
        mvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON).content(VEHICLES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Vehicle details accepted."))
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void planAssignsEachOrderToTheNearbyVehicle() throws Exception {
        postJson("/api/dispatch/orders", ORDERS);
        postJson("/api/dispatch/vehicles", VEHICLES);

        mvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchPlan.length()").value(2))
                .andExpect(jsonPath("$.dispatchPlan[0].vehicleId").value("VEH001"))
                .andExpect(jsonPath("$.dispatchPlan[0].totalLoad").value(10.0))
                .andExpect(jsonPath("$.dispatchPlan[0].totalDistance", matchesPattern("\\d+\\.\\d{2} km")))
                .andExpect(jsonPath("$.dispatchPlan[0].assignedOrders[0].orderId").value("ORD001"))
                .andExpect(jsonPath("$.dispatchPlan[0].assignedOrders[0].address")
                        .value("MG Road, Bangalore, Karnataka, India"))
                .andExpect(jsonPath("$.dispatchPlan[1].vehicleId").value("VEH002"))
                .andExpect(jsonPath("$.dispatchPlan[1].totalLoad").value(20.0))
                .andExpect(jsonPath("$.dispatchPlan[1].assignedOrders[0].orderId").value("ORD002"))
                .andExpect(jsonPath("$.unassignedOrders.length()").value(0));
    }

    @Test
    void resubmittingAnOrderIdUpdatesInsteadOfDuplicating() throws Exception {
        postJson("/api/dispatch/orders", ORDERS);
        postJson("/api/dispatch/orders", """
                {"orders":[{"orderId":"ORD001","latitude":12.9,"longitude":77.5,"address":"New Address",
                            "packageWeight":42,"priority":"LOW"}]}""");

        assertThat(orderRepository.count()).isEqualTo(2);
        var updated = orderRepository.findById("ORD001").orElseThrow();
        assertThat(updated.getPackageWeight()).isEqualTo(42.0);
        assertThat(updated.getAddress()).isEqualTo("New Address");
    }

    @Test
    void overweightOrderIsReportedAsUnassigned() throws Exception {
        postJson("/api/dispatch/orders", """
                {"orders":[{"orderId":"HUGE","latitude":12.9,"longitude":77.5,"address":"Somewhere",
                            "packageWeight":9999,"priority":"HIGH"}]}""");
        postJson("/api/dispatch/vehicles", VEHICLES);

        mvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unassignedOrders.length()").value(1))
                .andExpect(jsonPath("$.unassignedOrders[0].orderId").value("HUGE"))
                .andExpect(jsonPath("$.unassignedOrders[0].reason", containsString("exceeds")));
    }

    @Test
    void planWithoutVehiclesReturns422() throws Exception {
        postJson("/api/dispatch/orders", ORDERS);

        mvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message", containsString("No vehicles")));
    }

    @Test
    void invalidLatitudeAndWeightAreRejectedWithFieldErrors() throws Exception {
        mvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"orders":[{"orderId":"X","latitude":123,"longitude":77.5,"address":"A",
                                    "packageWeight":-5,"priority":"HIGH"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.errors", hasItem(containsString("latitude must be between -90 and 90"))))
                .andExpect(jsonPath("$.errors", hasItem(containsString("packageWeight must be greater than 0"))));
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void unknownPriorityIsRejectedWithAllowedValues() throws Exception {
        mvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"orders":[{"orderId":"X","latitude":12,"longitude":77,"address":"A",
                                    "packageWeight":5,"priority":"URGENT"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("HIGH")));
    }

    @Test
    void duplicateIdsWithinOneRequestAreRejected() throws Exception {
        mvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON).content("""
                        {"vehicles":[
                          {"vehicleId":"V1","capacity":10,"currentLatitude":1,"currentLongitude":1,"currentAddress":"A"},
                          {"vehicleId":"V1","capacity":20,"currentLatitude":2,"currentLongitude":2,"currentAddress":"B"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Duplicate vehicleId")));
    }

    @Test
    void emptyListIsRejected() throws Exception {
        mvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content("{\"orders\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("at least one order"))));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void zeroOrCapacityIsRejected() throws Exception {
        mvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON).content("""
                        {"vehicles":[{"vehicleId":"V1","capacity":0,"currentLatitude":1,"currentLongitude":1,
                                      "currentAddress":"A"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("capacity must be greater than 0"))));
    }

    @Test
    void resetClearsAllData() throws Exception {
        postJson("/api/dispatch/orders", ORDERS);
        postJson("/api/dispatch/vehicles", VEHICLES);

        mvc.perform(delete("/api/dispatch/reset")).andExpect(status().isOk());

        assertThat(orderRepository.count()).isZero();
        assertThat(vehicleRepository.count()).isZero();
    }

    @Test
    void swaggerDocsAreServed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/dispatch/plan']").exists());
    }
}
