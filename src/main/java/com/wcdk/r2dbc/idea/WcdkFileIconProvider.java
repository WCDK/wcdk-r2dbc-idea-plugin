package com.wcdk.r2dbc.idea;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.wcdk.r2dbc.idea.util.WcdkXmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public final class WcdkFileIconProvider implements FileIconProvider, DumbAware {
    @Override
    public @Nullable Icon getIcon(@NotNull VirtualFile file, int flags, @Nullable Project project) {
        if (project == null || !file.getName().endsWith(".xml")) return null;
        XmlFile xmlFile = PsiManager.getInstance(project).findFile(file) instanceof XmlFile xf ? xf : null;
        if (xmlFile == null) return null;
        return WcdkXmlUtil.isWcdkRepositoryFile(xmlFile) ? WcdkIcons.WCDK : null;
    }
}
