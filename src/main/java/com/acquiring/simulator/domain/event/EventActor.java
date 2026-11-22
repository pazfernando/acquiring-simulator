package com.acquiring.simulator.domain.event;

/** Component role that produced an observation (observation contract O1). */
public enum EventActor {
    CHANNEL,
    ACL,
    PROCESSOR,
    NETWORK,
    ISSUER
}
