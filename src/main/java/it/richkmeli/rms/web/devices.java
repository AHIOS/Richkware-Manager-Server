package it.richkmeli.rms.web;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.richkmeli.jframework.network.tcp.server.http.payload.response.KoResponse;
import it.richkmeli.jframework.network.tcp.server.http.payload.response.OkResponse;
import it.richkmeli.jframework.network.tcp.server.http.util.JServletException;
import it.richkmeli.jframework.orm.DatabaseException;
import it.richkmeli.rms.data.device.DeviceDatabaseManager;
import it.richkmeli.rms.data.device.model.Device;
import it.richkmeli.rms.web.util.RMSServletManager;
import it.richkmeli.rms.web.util.RMSSession;
import it.richkmeli.rms.web.util.RMSStatusCode;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Servlet implementation class DevicesListServlet
 */
@WebServlet("/devices")
public class devices extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public devices() {
        super();
    }

    /**
     * GET
     * get the device list. Every user can view only its own devices, admin users have
     * visibility to view all devices.
     *
     * @param request
     * @param response
     * @throws IOException
     */

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PrintWriter out = response.getWriter();
        try {
            RMSServletManager rmsServletManager = new RMSServletManager(request, response);
            rmsServletManager.doDefaultProcessRequest();
            rmsServletManager.checkLogin();

            // server session
            RMSSession rmsSession = rmsServletManager.getRMSServerSession();
            DeviceDatabaseManager databaseManager = rmsSession.getDeviceDatabaseManager();
            List<Device> devices = null;
            if (rmsSession.isAdmin()) {
                // if the user is an Admin, it gets the list of all devices
                devices = databaseManager.getAllDevices();
            } else {
                devices = databaseManager.getUserDevices(rmsSession.getUserID());
            }

            String deviceListJSON = generateDevicesListJSON(devices);

            String message = rmsServletManager.doDefaultProcessResponse(deviceListJSON);
            out.println((new OkResponse(RMSStatusCode.SUCCESS, message)).json());

            out.flush();
            out.close();

        } catch (JServletException e) {
            out.println(e.getKoResponseJSON());
        } catch (DatabaseException e) {
            out.println((new KoResponse(RMSStatusCode.DB_ERROR, e.getMessage())).json());
        } catch (Exception e) {
            //e.printStackTrace();
            out.println((new KoResponse(RMSStatusCode.GENERIC_ERROR, e.getMessage())).json());
        }
    }

    private String generateDevicesListJSON(List<Device> devices) {
        Type type = new TypeToken<List<Device>>() {
        }.getType();
        Gson gson = new Gson();

        // oggetto -> gson
        String devicesJSON = gson.toJson(devices, type);

        return devicesJSON;
    }

}

