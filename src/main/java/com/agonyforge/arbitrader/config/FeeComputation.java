package com.agonyforge.arbitrader.config;

/**
 * Are fees computed on the client or the server?
 */
public enum FeeComputation {
    SERVER, // fees are computed on the server
    CLIENT  // fees must be accounted for by the client (that's us!)
}
