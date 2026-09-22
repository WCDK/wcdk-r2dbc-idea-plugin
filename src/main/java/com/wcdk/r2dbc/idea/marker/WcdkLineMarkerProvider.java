package com.wcdk.r2dbc.idea.marker;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.Function;
import com.intellij.openapi.vfs.VirtualFile;
import com.wcdk.r2dbc.idea.WcdkIcons;
import com.wcdk.r2dbc.idea.util.WcdkXmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WcdkLineMarkerProvider implements LineMarkerProvider {
    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        // LineMarkerProvider is queried for leaf PSI elements. Attaching the XML
        // marker to the opening tag's name token also prevents the marker from
        // being discarded when IntelliJ merges the visible-range highlighting.
        if (element instanceof XmlToken token && token.getParent() instanceof XmlAttributeValue value) {
            return xmlMarker(value, token);
        }
        if (element instanceof XmlToken token && token.getParent() instanceof XmlTag tag
                && tag.getNode().findChildByType(XmlTokenType.XML_NAME) == token.getNode()) {
            return xmlTagMarker(tag, token);
        }
        if (element instanceof PsiIdentifier identifier) return javaMarker(identifier);
        return null;
    }

    private LineMarkerInfo<?> xmlTagMarker(XmlTag tag, PsiElement anchor) {
        if (!(tag.getContainingFile() instanceof XmlFile xmlFile)) return null;
        if (!WcdkXmlUtil.isWcdkRepositoryFile(xmlFile)) return null;
        XmlTag root = xmlFile.getRootTag();
        if (tag == root) {
            String namespace = WcdkXmlUtil.attributeValue(root, "namespace");
            PsiElement target = namespace == null ? null :
                    WcdkXmlUtil.findClass(tag.getProject(), namespace);
            return target == null ? null :
                    marker(anchor, "WCDK: Go to repository interface", (event, ignored) -> navigate(target));
        }
        String id = WcdkXmlUtil.attributeValue(tag, "id");
        if (id != null && !id.isBlank()) {
            String namespace = WcdkXmlUtil.namespace(tag);
            var repository = WcdkXmlUtil.findClass(tag.getProject(), namespace);
            PsiElement target = WcdkXmlUtil.findMethod(repository, id);
            return target == null ? null :
                    marker(anchor, "WCDK: Go to repository method", (event, ignored) -> navigate(target));
        }
        return null;
    }

    private LineMarkerInfo<?> xmlMarker(XmlAttributeValue value, PsiElement anchor) {
        if (!(value.getParent() instanceof XmlAttribute attribute)) return null;
        String name = attribute.getLocalName();
        // namespace and statement id already have a marker on their opening tag.
        // Adding another marker to the attribute value produces two gutter icons
        // for the same navigation target.
        if ("namespace".equals(name) || "id".equals(name)) return null;
        PsiElement target = value.getReferences().length == 0 ? null : value.getReferences()[0].resolve();
        if (target == null) return null;
        String tooltip = switch (name) {
            case "resultType", "type", "javaType" -> "WCDK: Go to entity class";
            case "parameterType" -> "WCDK: Go to parameter class";
            case "property" -> "WCDK: Go to entity property";
            case "resultMap" -> "WCDK: Go to result map";
            default -> null;
        };
        if (tooltip == null) return null;
        return marker(anchor, tooltip, (event, ignored) -> navigate(target));
    }

    private LineMarkerInfo<?> javaMarker(PsiIdentifier identifier) {
        PsiElement parent = identifier.getParent();
        if (parent instanceof PsiClass psiClass) {
            return javaClassMarker(psiClass);
        }
        if (parent instanceof PsiMethod method && method.getContainingClass() != null) {
            return javaMethodMarker(method);
        }
        return null;
    }

    private LineMarkerInfo<?> javaClassMarker(PsiClass psiClass) {
        PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
        if (nameIdentifier == null || psiClass.getQualifiedName() == null) return null;
        XmlTarget target = findRepositoryXml(psiClass.getProject(), psiClass.getQualifiedName(), null);
        return target == null ? null : marker(nameIdentifier, "WCDK: Go to repository XML",
                (event, ignored) -> navigate(target.element));
    }

    private LineMarkerInfo<?> javaMethodMarker(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        PsiIdentifier nameIdentifier = method.getNameIdentifier();
        if (containingClass == null || containingClass.getQualifiedName() == null || nameIdentifier == null) {
            return null;
        }
        XmlTarget target = findRepositoryXml(method.getProject(), containingClass.getQualifiedName(), method.getName());
        return target == null ? null : marker(nameIdentifier, "WCDK: Go to WCDK XML statement",
                (event, ignored) -> navigate(target.element));
    }

    private static LineMarkerInfo<PsiElement> marker(PsiElement element,
                                                       String tooltip,
                                                       GutterIconNavigationHandler<PsiElement> handler) {
        TextRange range = element.getTextRange();
        return new LineMarkerInfo<PsiElement>(element, range, WcdkIcons.WCDK,
                (Function<PsiElement, String>) ignored -> tooltip,
                handler, GutterIconRenderer.Alignment.CENTER, () -> tooltip);
    }

    private static void navigate(PsiElement element) {
        if (element == null) return;
        VirtualFile file = element.getContainingFile().getVirtualFile();
        if (file == null) return;
        new OpenFileDescriptor(element.getProject(), file, element.getTextOffset()).navigate(true);
    }

    private static XmlTarget findRepositoryXml(Project project, String namespace, @Nullable String methodName) {
        if (namespace == null) return null;
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        for (VirtualFile file : FilenameIndex.getAllFilesByExt(project, "xml", scope)) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (!(psiFile instanceof XmlFile xmlFile) || !WcdkXmlUtil.isWcdkRepositoryFile(xmlFile)) continue;
            XmlTag root = xmlFile.getRootTag();
            if (!namespace.equals(WcdkXmlUtil.attributeValue(root, "namespace"))) continue;
            XmlTag target = methodName == null ? root : WcdkXmlUtil.statementForMethod(xmlFile, namespace, methodName);
            if (target != null) return new XmlTarget(target);
        }
        return null;
    }

    private record XmlTarget(XmlTag element) {
    }
}
