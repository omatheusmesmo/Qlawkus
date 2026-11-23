package dev.omatheusmesmo.qlawkus.heartbeat;

import java.util.Map;

interface HeartbeatDecisionModel {

    HeartbeatDecision decide(Map<String, Object> fields);
}
