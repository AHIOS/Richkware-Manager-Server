package it.richkmeli.rms.web;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.richkmeli.jframework.crypto.Crypto;
import it.richkmeli.jframework.network.tcp.server.http.payload.response.KoResponse;
import it.richkmeli.jframework.network.tcp.server.http.payload.response.OkResponse;
import it.richkmeli.jframework.network.tcp.server.http.util.JServletException;
import it.richkmeli.jframework.util.RandomStringGenerator;
import it.richkmeli.jframework.util.log.Logger;
import it.richkmeli.rms.data.device.DeviceDatabaseManager;
import it.richkmeli.rms.data.device.model.Device;
import it.richkmeli.rms.web.util.RMSServletManager;
import it.richkmeli.rms.web.util.RMSSession;
import it.richkmeli.rms.web.util.RMSStatusCode;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Servlet implementation class DevicesListServlet
 */
@WebServlet("/device")
public class device extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final int keyLength = 32;
    private String password;


    public device() {
        super();
        password = ResourceBundle.getBundle("configuration").getString("encryptionkey");
    }

    /**
     * PUT
     * put device to RMS, if for the device is te first call, all the payload is encrypted
     * with the preshared key, otherwise the first key (data0) is encrypted with preshared
     * and others with server-side generated key for specific device (returned at the first
     * call)
     *
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws IOException
     */

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PrintWriter out = response.getWriter();
        try {
            RMSServletManager rmsServletManager = new RMSServletManager(request, response);
            Map<String, String> attribMap = rmsServletManager.doDefaultProcessRequest(false);

            // server session
            RMSSession rmsSession = rmsServletManager.getRMSServerSession();

            // data0: contains deviceID (always encrypted with preshared key)
            // data1: contains serverPort
            // data2: contains associatedUser
            if (attribMap.containsKey("data0") &&
                    attribMap.containsKey("data1") &&
                    attribMap.containsKey("data2")) {

                String data0 = attribMap.get("data0");
                String data1 = attribMap.get("data1");
                String data2 = attribMap.get("data2");

                //Logger.info("data0: " + data0 + " data1: " + data1 + " data2: " + data2);

                String name = Crypto.decryptRC4(data0, password);

                // check in the DB if there is an entry with that name
                DeviceDatabaseManager deviceDatabaseManager = rmsSession.getDeviceDatabaseManager();
                Device oldDevice = deviceDatabaseManager.getDevice(name);

                // if this entry exists, then it's used to decrypt the encryption key in the DB
                String serverPort;
                String associatedUser;
                // at the first call is encrypted with preshared key, at the following with server-side generated key
                if (oldDevice == null) {
                    serverPort = Crypto.decryptRC4(data1, password);
                    associatedUser = Crypto.decryptRC4(data2, password);
                } else {
                    serverPort = Crypto.decryptRC4(data1, oldDevice.getEncryptionKey());
                    associatedUser = Crypto.decryptRC4(data2, oldDevice.getEncryptionKey());
                }

                String encryptionKey = RandomStringGenerator.generateAlphanumericString(keyLength);

                String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());

                Device newDevice = new Device(
                        name,
                        request.getRemoteAddr(),
                        serverPort,
                        timeStamp,
                        encryptionKey,
                        associatedUser,
                        "",
                        "");

                Logger.info("SERVLET device, doGet: Device: " + name + " " + request.getRemoteAddr() + " " + serverPort + " " + timeStamp + " " + encryptionKey + " " + associatedUser + " ");

                String message = "";
                if (oldDevice == null) {
                    deviceDatabaseManager.addDevice(newDevice);
                    message = "Device " + newDevice.getName() + " added.";
                } else {
                    // do not change Encryption Key
                    newDevice.setEncryptionKey(oldDevice.getEncryptionKey());
                    deviceDatabaseManager.editDevice(newDevice);
                    message = "Device " + newDevice.getName() + " updated.";
                }

                message = rmsServletManager.doDefaultProcessResponse(message);
                out.println((new OkResponse(RMSStatusCode.SUCCESS, message)).json());
            } else {
                // argomenti non presenti
                out.println((new KoResponse(RMSStatusCode.GENERIC_ERROR, "Parameters missing")).json());
            }
        } catch (JServletException e) {
            out.println(e.getKoResponseJSON());
        } catch (Exception e) {
            e.printStackTrace();
            out.println((new KoResponse(RMSStatusCode.GENERIC_ERROR, e.getMessage())).json());
        }

        out.flush();
        out.close();

    }

    /**
     * DELETE
     * delete device from device list. Every user can delete only its own device, admin user
     * can delete all devices
     *
     * @param req
     * @param resp
     * @throws javax.servlet.ServletException
     * @throws IOException
     */

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws javax.servlet.ServletException, IOException {
        //if the code below is de-commented, this servlet disables DELETE
        //super.doDelete(req, resp);
        HttpSession httpSession = req.getSession();
        RMSSession rmsSession = null;
        try {
            RMSServletManager rmsServletManager = new RMSServletManager(req, resp);
            rmsSession = rmsServletManager.getRMSServerSession();
        } catch (JServletException e) {
            httpSession.setAttribute("error", e);
            req.getRequestDispatcher(RMSServletManager.ERROR_JSP).forward(req, resp);

        }

        try {
            String out = null;

            String user = rmsSession.getUserID();
            // Authentication
            if (user != null) {
                if (req.getParameterMap().containsKey("name")) {
                    String name = req.getParameter("name");

                    Device device = rmsSession.getDeviceDatabaseManager().getDevice(name);

                    if (device.getAssociatedUser().compareTo(rmsSession.getUserID()) == 0 ||
                            rmsSession.isAdmin()) {
                        rmsSession.getDeviceDatabaseManager().removeDevice(name);
                        out = (new OkResponse(RMSStatusCode.SUCCESS, "Device " + name + " deleted.")).json();
                    } else {
                        // TODO rimanda da qualche parte perche c'è errore
                        httpSession.setAttribute("error", "non hai i privilegi");
                        req.getRequestDispatcher(RMSServletManager.LOGIN_HTML).forward(req, resp);
                    }

                } else {
                    // TODO rimanda da qualche parte perche c'è errore
                    httpSession.setAttribute("error", "dispositivo non specificato");
                    req.getRequestDispatcher(RMSServletManager.LOGIN_HTML).forward(req, resp);
                }
                // servlet response
                PrintWriter printWriter = resp.getWriter();
                printWriter.println(out);
                printWriter.flush();
                printWriter.close();
            } else {
                // non loggato
                // TODO rimanda da qualche parte perche c'è errore
                httpSession.setAttribute("error", "non loggato");
                req.getRequestDispatcher(RMSServletManager.LOGIN_HTML).forward(req, resp);
            }
        } catch (Exception e) {
            // redirect to the JSP that handles errors
            httpSession.setAttribute("error", e);
            req.getRequestDispatcher(RMSServletManager.ERROR_JSP).forward(req, resp);
        }

    }

    private String GenerateDevicesListJSON(Device device/*, Session session*/) {
        //DatabaseManager databaseManager = session.getDatabaseManager();
        List<Device> devicesList = new ArrayList<>();

        devicesList.add(device);

        Type type = new TypeToken<List<Device>>() {
        }.getType();
        Gson gson = new Gson();

        // oggetto -> gson
        String devicesListJSON = gson.toJson(devicesList, type);

        return devicesListJSON;
    }

}

