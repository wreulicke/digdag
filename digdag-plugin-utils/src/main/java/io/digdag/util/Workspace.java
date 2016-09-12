package io.digdag.util;

import com.google.common.io.CharStreams;

import io.digdag.spi.TaskRequest;
import io.digdag.spi.TemplateEngine;
import io.digdag.spi.TemplateException;
import io.digdag.client.config.Config;
import io.digdag.client.config.ConfigException;

import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.nio.file.Files;
import java.nio.charset.Charset;

public class Workspace
    implements Closeable
{
    public static Path workspacePath(Path projectPath, TaskRequest taskRequest)
    {
        return workspacePath(projectPath, taskRequest.getConfig().get("_workdir", String.class, ""));
    }

    public static Path workspacePath(Path projectPath, String workdir)
    {
        Path path = projectPath.resolve(workdir).normalize().toAbsolutePath();
        if (!path.toString().startsWith(projectPath.toString())) {
            throw new IllegalArgumentException("Working directory must not be outside of project path (" + projectPath + "): " + workdir);
        }
        return path;
    }

    public static Workspace ofTaskRequest(Path projectPath, TaskRequest taskRequest)
    {
        return of(projectPath, taskRequest.getConfig().get("_workdir", String.class, ""));
    }

    public static Workspace of(Path projectPath, String workdir)
    {
        return new Workspace(projectPath, workdir);
    }

    private final Path projectPath;
    private final List<Path> tempFilePaths = new ArrayList<>();
    private Path workspacePath;

    private Workspace(Path projectPath, String workdir)
    {
        this.projectPath = projectPath.normalize().toAbsolutePath();
        this.workspacePath = workspacePath(projectPath, workdir);
    }

    public Path getProjectPath()
    {
        return projectPath;
    }

    public Path getPath()
    {
        return workspacePath;
    }

    public Path getPath(String fileName)
    {
        Path path = workspacePath.resolve(fileName);
        if (!path.normalize().toString().startsWith(projectPath.toString())) {
            throw new IllegalArgumentException("File name must not be outside of project path (" + projectPath + "): " + fileName);
        }
        return path;
    }

    public File getFile(String relative)
    {
        return getPath(relative).toFile();
    }

    public String createTempFile(String prefix, String suffix)
        throws IOException
    {
        // file will be deleted by WorkspaceManager
        Path file = Files.createTempFile(getTempDir(), prefix, suffix);
        tempFilePaths.add(file);
        return workspacePath.relativize(file).toString();
    }

    public InputStream newInputStream(String fileName)
        throws IOException
    {
        return Files.newInputStream(getPath(fileName));
    }

    public BufferedReader newBufferedReader(String fileName, Charset cs)
        throws IOException
    {
        return Files.newBufferedReader(getPath(fileName), cs);
    }

    public OutputStream newOutputStream(String fileName)
        throws IOException
    {
        return Files.newOutputStream(getPath(fileName));
    }

    public BufferedWriter newBufferedWriter(String fileName, Charset cs)
        throws IOException
    {
        return Files.newBufferedWriter(getPath(fileName), cs);
    }

    private synchronized Path getTempDir()
        throws IOException
    {
        // <projectDir>/.digdag/tmp
        Path dir = projectPath.resolve(".digdag/tmp");
        Files.createDirectories(dir);
        return dir;
    }

    public String templateFile(TemplateEngine templateEngine, String fileName, Charset fileCharset, Config params)
        throws IOException, TemplateException
    {
        try (BufferedReader reader = newBufferedReader(fileName, fileCharset)) {
            String content = CharStreams.toString(reader);
            return templateEngine.template(content, params);
        }
    }

    public String templateCommand(TemplateEngine templateEngine, Config params, String aliasKey, Charset fileCharset)
    {
        if (params.has("_command")) {
            Config nested;
            try {
                nested = params.getNested("_command");
            }
            catch (ConfigException notNested) {
                String command = params.get("_command", String.class);
                try {
                    return templateFile(templateEngine, command, fileCharset, params);
                }
                catch (ConfigException ex) {
                    throw ex;
                }
                catch (TemplateException ex) {
                    throw new ConfigException("" + ex.getMessage() + " in " + command, ex);
                }
                catch (FileNotFoundException | NoSuchFileException ex) {
                    throw new ConfigException("File not found: " + ex.getMessage(), ex);
                }
                catch (RuntimeException | IOException ex) {
                    throw new ConfigException("Failed to read a template file: " + command + ": " + ex.getClass(), ex);
                }
            }
            // ${...} in nested parameters are already evaluated. no needs to call template.
            return nested.get("data", String.class);
        }
        else if (aliasKey != null) {
            return params.get(aliasKey, String.class);
        }
        else {
            return params.get("_command", String.class);  // this causes ConfigException with appropriate message
        }
    }

    @Override
    public void close()
    {
        for (Path path : tempFilePaths) {
            try {
                Files.deleteIfExists(path);
            }
            catch (IOException ex) {
                // TODO show warning log
            }
        }
    }
}
