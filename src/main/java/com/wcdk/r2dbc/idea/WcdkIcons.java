package com.wcdk.r2dbc.idea;


import javax.swing.Icon;
import com.intellij.openapi.util.IconLoader;
public final class WcdkIcons {
    /**
     * Compact mark used in the editor gutter. The standalone mark stays legible
     * at IntelliJ's 16px gutter size; the full wordmark is packaged separately
     * as {@code /icons/wcdk-logo.png} for plugin branding.
     */
    public static final Icon WCDK = IconLoader.getIcon("/icons/wcdk.svg", WcdkIcons.class);

    private WcdkIcons() {
    }
}
