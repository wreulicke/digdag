package io.digdag.cli.client;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Response;
import com.beust.jcommander.Parameter;
import io.digdag.cli.Main;
import io.digdag.cli.Command;
import io.digdag.cli.SystemExitException;
import io.digdag.client.DigdagClient;
import static io.digdag.cli.Main.systemExit;

public abstract class ClientCommand
    extends Command
{
    @Parameter(names = {"-e", "--endpoint"}, required = true)
    protected String endpoint;

    @Override
    public void main()
        throws Exception
    {
        try {
            mainWithClientException();
        }
        catch (ClientErrorException ex) {
            Response res = ex.getResponse();
            switch (res.getStatus()) {
            case 404:  // NOT_FOUND
                throw systemExit("Resource not found: " + res.readEntity(String.class));
            case 409:  // CONFLICT
                throw systemExit("Request conflicted: " + res.readEntity(String.class));
            case 422:  // UNPROCESSABLE_ENTITY
                throw systemExit("Invalid option: " + res.readEntity(String.class));
            default:
                throw systemExit("Status code " + res.getStatus() + ": " + res.readEntity(String.class));
            }
        }
    }

    public abstract void mainWithClientException()
        throws Exception;

    protected DigdagClient buildClient()
    {
        String[] fragments = endpoint.split(":", 2);
        String host;
        int port;
        if (fragments.length == 1) {
            host = fragments[0];
            port = 80;
        }
        else {
            host = fragments[0];
            port = Integer.parseInt(fragments[1]);
        }

        return DigdagClient.builder()
            .host(host)
            .port(port)
            .build();
    }

    public static void showCommonOptions()
    {
        System.err.println("    -e, --endpoint HOST[:PORT]       HTTP endpoint");
        Main.showCommonOptions();
    }

    public long parseLongOrUsage(String arg)
        throws SystemExitException
    {
        try {
            return Long.parseLong(args.get(0));
        }
        catch (NumberFormatException ex) {
            throw usage(ex.getMessage());
        }
    }

    protected ModelPrinter modelPrinter()
    {
        return new ModelPrinter();
    }
}
