import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by sbai on 5/27/16.
 * This is a simple demo of the usage of Google Cloud Engine Java API
 */
public class Main {

    private static final String APPLICATION_NAME = "MyCompany-ProductName/1.0";
    private static final String INSTRUCTION = "Please input your commands:";
    private static final String MACHINE_TYPE = "https://www.googleapis" +
            ".com/compute/v1/projects/%s/zones/%s/machineTypes/%s";
    private static final String NETWORK_INTERFACE = "https://www.googleapis" +
            ".com/compute/v1/projects/%s/global/networks/default";
    private static final String NETWORK_INTERFACE_CONFIG = "ONE_TO_ONE_NAT";
    private static final String NETWORK_ACCESS_CONFIG = "External NAT";
    private static final String SOURCE_IMAGE =
            "https://www.googleapis.com/compute/v1/projects/ubuntu-os-cloud/global/images/family" +
                    "/ubuntu-1404-lts";
    private static final String DISK_TYPE = "https://www.googleapis" +
            ".com/compute/v1/projects/%s/zones/%s/diskTypes/pd-standard";
    private static final String ACCOUNT_EMAIL = "test-383@test-1323.iam.gserviceaccount.com";
    private static final String FULL_CONTROL_SCOPE = "https://www.googleapis.com/auth/devstorage" +
            ".full_control";
    private static final String COMPUTE_SCOPE = "https://www.googleapis.com/auth/compute";
    private static final long WAIT_INTERVAL = 5000;

    public static void main(String[] args) {
        String[] scopes = {ComputeScopes.DEVSTORAGE_FULL_CONTROL, ComputeScopes.COMPUTE};
        try {
            File file = new File("/Users/sbai/.credentials/Test-60026ac2a6f5.json");
            GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream
                    (file)).createScoped(Arrays.asList(scopes));
//            GoogleCredential credential = GoogleCredential.getApplicationDefault();
//            if (credential.createScopedRequired()) {
//                credential = credential.createScoped(Arrays.asList(scopes));
//            }

            Compute compute = new Compute.Builder(GoogleNetHttpTransport.newTrustedTransport(),
                    JacksonFactory.getDefaultInstance(), credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

            while (true) {
                System.out.println(INSTRUCTION);
                String cmd = br.readLine();
                switch (cmd.toLowerCase().trim()) {
                    case "new":
                        startInstance(compute, br);
                        break;
                    case "show":
                        showInstances(compute, br);
                        break;
                    case "exit":
                        br.close();
                        return;
                    default:
                        printHelper(cmd);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void startInstance(Compute compute, BufferedReader br) throws IOException {
        System.out.println("Please input the project id:");
        String projectId = br.readLine();
        System.out.println("Please input the zone name:");
        String zoneName = br.readLine();
        System.out.println("Please input the instance name:");
        String instanceName = br.readLine();
        System.out.println("Please input the instance type:");
        String instanceType = br.readLine();

        Instance instance = new Instance();
        instance.setName(instanceName);
        instance.setMachineType(String.format(MACHINE_TYPE, projectId, zoneName, instanceType));

        NetworkInterface ifc = new NetworkInterface();
        ifc.setNetwork(String.format(NETWORK_INTERFACE, projectId));

        List<AccessConfig> configs = new ArrayList<>();
        AccessConfig config = new AccessConfig();
        config.setType(NETWORK_INTERFACE_CONFIG);
        config.setName(NETWORK_ACCESS_CONFIG);
        configs.add(config);

        ifc.setAccessConfigs(configs);
        instance.setNetworkInterfaces(Collections.singletonList(ifc));

        AttachedDisk disk = new AttachedDisk();
        disk.setBoot(true);
        disk.setAutoDelete(true);
        disk.setType("PERSISTENT");
        AttachedDiskInitializeParams params = new AttachedDiskInitializeParams();

        params.setDiskName(instanceName);
        params.setSourceImage(SOURCE_IMAGE);
        params.setDiskType(String.format(DISK_TYPE, projectId, zoneName));

        disk.setInitializeParams(params);
        instance.setDisks(Collections.singletonList(disk));

        ServiceAccount account = new ServiceAccount();
        account.setEmail(ACCOUNT_EMAIL);

        List<String> scopes = new ArrayList<>();
        scopes.add(FULL_CONTROL_SCOPE);
        scopes.add(COMPUTE_SCOPE);

        account.setScopes(scopes);
        instance.setServiceAccounts(Collections.singletonList(account));

        System.out.println("Provisioning the following instance:");
        System.out.println(instance.toPrettyString());
        Compute.Instances.Insert insert = compute.instances().insert(projectId, zoneName, instance);

        Operation operation = insert.execute();
        String zone = operation.getZone();
        if (zone != null) {
            String[] bits = zone.split("/");
            zone = bits[bits.length - 1];
        }
        String opId = operation.getName();

        while (operation != null && !operation.getStatus().equals("DONE")) {
            System.out.println("Waiting for provisioning...");
            try {
                Thread.sleep(WAIT_INTERVAL);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (zone != null) {
                Compute.ZoneOperations.Get get = compute.zoneOperations().get(projectId, zone,
                        opId);
                operation = get.execute();
            } else {
                Compute.GlobalOperations.Get get = compute.globalOperations().get(projectId, opId);
                operation = get.execute();
            }
        }

        if (operation != null) {
            System.out.println(operation.getError());
        }
    }

    private static void showInstances(Compute compute, BufferedReader br) throws IOException {
        System.out.println("Please input the project id:");
        String projectId = br.readLine();
        System.out.println("Please input the zone name:");
        String zoneName = br.readLine();
        System.out.println("================= Listing Compute Engine Instances =================");
        Compute.Instances.List instances = compute.instances().list(projectId, zoneName);
        InstanceList list = instances.execute();
        if (list.getItems() == null) {
            System.out.println("There are no instances in your specified area");
        } else {
            for (Instance instance : list.getItems()) {
                System.out.println(instance.toPrettyString());
            }
        }
        System.out.println("=============================== End ===============================");
    }

    private static void printHelper(String cmd) {
        System.out.println(cmd + " not implemented, please enter one of the following commands:");
        System.out.println("1. new (create a new instance)");
        System.out.println("2. show (show all instances)");
        System.out.println("3. exit (exit the program)");
    }

}
