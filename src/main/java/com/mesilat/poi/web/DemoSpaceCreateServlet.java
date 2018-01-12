package com.mesilat.poi.web;

import com.atlassian.confluence.importexport.DefaultImportContext;
import com.atlassian.confluence.importexport.ImportExportException;
import com.atlassian.confluence.importexport.ImportExportManager;
import com.atlassian.confluence.importexport.impl.ExportDescriptor;
import com.atlassian.confluence.importexport.impl.UnexpectedImportZipFileContents;
import com.atlassian.confluence.rpc.NotPermittedException;
import com.atlassian.confluence.rpc.RemoteException;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Scanned
public class DemoSpaceCreateServlet extends HttpServlet {
    private final PermissionManager permissionManager;
    private final SpaceManager spaceManager;
    private final ImportExportManager importExportManager;
    private final SettingsManager settingsManager;


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {
            importSpace();
            response.sendRedirect(settingsManager.getGlobalSettings().getBaseUrl() + "/display/POIDD/POI+Forms+Addon");
        } catch (RemoteException ex) {
            throw new ServletException(ex);
        }
    }
    @Override
    public String getServletInfo() {
        return "Create demo space from backup";
    }

    public void importSpace() throws NotPermittedException, RemoteException{
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (!permissionManager.hasPermission(user, Permission.ADMINISTER, PermissionManager.TARGET_APPLICATION)) {
            throw new NotPermittedException("You don't have the 'Confluence Administrator' permission.");
        }
        try {
            File tmp = createTempFile("confluence-import-", ".tmp", getSpace());
            ExportDescriptor descriptor = ExportDescriptor.getExportDescriptor(tmp);
            if (!descriptor.isSpaceImport()) {
                throw new RemoteException("Invalid import type - can only import spaces");
            }
            String spaceKey = descriptor.getSpaceKey();
            if (spaceManager.getSpace(spaceKey) != null) {
                throw new RemoteException("Space " + spaceKey + " already exists.  Import aborted.");
            }

            DefaultImportContext defaultImportContext = new DefaultImportContext(tmp.getAbsolutePath(), user);
            defaultImportContext.setDeleteWorkingFile(true);
            defaultImportContext.setSpaceKeyOfSpaceImport(spaceKey);

            importExportManager.doImport(defaultImportContext);
            for (int i = 0; i < 100; i++){
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {
                    break;
                }
                if (defaultImportContext.getProgressMeter().getPercentageComplete() == 100){
                    break;
                }
            }
            if (defaultImportContext.getProgressMeter().getPercentageComplete() < 100){
                throw new RemoteException(String.format("Space import is still at %d percent",
                    defaultImportContext.getProgressMeter().getPercentageComplete())
                );
            }
        } catch (ImportExportException | IOException | UnexpectedImportZipFileContents ex) {
            throw new RemoteException("Could not import space", ex);
        }
    }
    private File createTempFile(String suffix, String prefix, InputStream in) throws IOException {
        File tmp = File.createTempFile(suffix, prefix);
        try (FileOutputStream fout = new FileOutputStream(tmp)){
            ByteStreams.copy(in, fout);
        } finally {
            try {
                if (in != null){
                    in.close();
                }
            } catch (IOException ignore) {
            }
        }
        return tmp;
    }
    private InputStream getSpace(){
        return Thread.currentThread().getContextClassLoader().getResourceAsStream("/demo-space.xml.zip");
    }

    @Inject
    public DemoSpaceCreateServlet(
        final PermissionManager permissionManager,
        final SpaceManager spaceManager,
        final ImportExportManager importExportManager,
        final SettingsManager settingsManager
    ){
        this.permissionManager = permissionManager;
        this.spaceManager = spaceManager;
        this.importExportManager = importExportManager;
        this.settingsManager = settingsManager;
    }
}