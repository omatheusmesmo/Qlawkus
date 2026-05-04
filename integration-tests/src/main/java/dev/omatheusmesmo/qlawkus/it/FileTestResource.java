package dev.omatheusmesmo.qlawkus.it;

import dev.omatheusmesmo.qlawkus.dto.FileEntry;
import dev.omatheusmesmo.qlawkus.dto.FileResult;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tool.shell.FileTool;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/test/file")
@Authenticated
public class FileTestResource {

    @Inject
    @ClawTool
    FileTool fileTool;

    @GET
    @Path("/read")
    public Response readFile(@QueryParam("path") String path) {
        FileResult result = fileTool.readFile(path);
        return Response.ok(result).build();
    }

    @GET
    @Path("/write")
    public Response writeFile(@QueryParam("path") String path, @QueryParam("content") String content) {
        FileResult result = fileTool.writeFile(path, content);
        return Response.ok(result).build();
    }

    @GET
    @Path("/list")
    public Response listFiles(@QueryParam("path") String path) {
        List<FileEntry> entries = fileTool.listFiles(path);
        return Response.ok(entries).build();
    }

    @GET
    @Path("/mkdir")
    public Response makeDirectory(@QueryParam("path") String path) {
        FileResult result = fileTool.makeDirectory(path);
        return Response.ok(result).build();
    }

    @GET
    @Path("/delete")
    public Response deleteFile(@QueryParam("path") String path) {
        FileResult result = fileTool.deleteFile(path);
        return Response.ok(result).build();
    }
}
