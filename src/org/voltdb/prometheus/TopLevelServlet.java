/*
 * This file is part of VoltDB.
 * Copyright (C) 2019-2020 VoltDB Inc.
 */

package org.voltdb.prometheus;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class TopLevelServlet extends HttpServlet {

    private final String serverList;
    private final int port;

    public TopLevelServlet(String serverList, int port) {
        this.serverList = serverList;
        this.port = port;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        resp.getWriter().println(String.format("VoltDB Prometheus Agent for %s port %s",
                                               serverList, port));
    }
}
