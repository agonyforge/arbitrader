package com.r307.arbitrader.config;

public enum FeeComputation {
    SERVER, // fees are computed on the server
    CLIENT  // fees must be accounted for by the client (that's us!)
}
