package com.wcdk.r2dbc.idea.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;
import java.util.List;

public final class WcdkXmlUtil {
    private WcdkXmlUtil() {
    }

    public static boolean isWcdkRepositoryFile(PsiFile file) {
        if (!(file instanceof XmlFile xmlFile) || xmlFile.getRootTag() == null) return false;
        if (!"repository".equals(xmlFile.getRootTag().getLocalName())) return false;
        var prolog = xmlFile.getDocument().getProlog();
        var doctype = prolog == null ? null : prolog.getDoctype();
        return doctype != null && WCDK_DOCTYPE.equals(doctype.getPublicId());
    }

    public static final String WCDK_DOCTYPE = "-//WCDK/wcdk-r2dbc";

    public static String attributeValue(XmlTag tag, String name) {
        if (tag == null) return null;
        XmlAttribute attribute = tag.getAttribute(name);
        if (attribute == null || attribute.getValue() == null) return null;
        return attribute.getValue().trim();
    }

    public static XmlTag repositoryRoot(PsiElement element) {
        PsiFile file = element.getContainingFile();
        return file instanceof XmlFile xmlFile && isWcdkRepositoryFile(file) ? xmlFile.getRootTag() : null;
    }

    public static String namespace(PsiElement element) {
        XmlTag root = repositoryRoot(element);
        return root == null ? null : attributeValue(root, "namespace");
    }

    public static PsiClass findClass(Project project, String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) return null;
        return JavaPsiFacade.getInstance(project).findClass(
                qualifiedName.trim(), GlobalSearchScope.projectScope(project));
    }

    public static PsiMethod findMethod(PsiClass repository, String methodName) {
        if (repository == null || methodName == null || methodName.isBlank()) return null;
        PsiMethod[] methods = repository.findMethodsByName(methodName.trim(), true);
        return methods.length == 0 ? null : methods[0];
    }

    public static PsiClass resultClass(Project project, XmlTag statement) {
        String resultType = attributeValue(statement, "resultType");
        if (resultType == null) resultType = attributeValue(statement, "type");
        if (resultType == null) {
            XmlTag resultMap = enclosingTag(statement, "resultMap");
            resultType = resultMap == null ? null : attributeValue(resultMap, "type");
        }
        return findClass(project, resultType);
    }

    public static XmlTag findTagById(XmlFile file, String id) {
        if (!isWcdkRepositoryFile(file) || id == null) return null;
        String localId = id.trim();
        int separator = localId.lastIndexOf('.');
        if (separator >= 0) localId = localId.substring(separator + 1);
        for (XmlTag tag : statementTags(file.getRootTag())) {
            if (localId.equals(attributeValue(tag, "id"))) return tag;
        }
        return null;
    }

    /**
     * Resolves a resultMap reference in the current repository. Both the short
     * form ("userMap") and the fully qualified form ("Repository.userMap")
     * are accepted, matching the runtime XML registry.
     */
    public static XmlTag findResultMapById(XmlFile file, String id) {
        if (!isWcdkRepositoryFile(file) || id == null) return null;
        String localId = id.trim();
        int separator = localId.lastIndexOf('.');
        if (separator >= 0) localId = localId.substring(separator + 1);
        for (XmlTag tag : statementTags(file.getRootTag())) {
            if ("resultMap".equals(tag.getLocalName()) && localId.equals(attributeValue(tag, "id"))) {
                return tag;
            }
        }
        return null;
    }

    public static XmlTag enclosingTag(PsiElement element, String localName) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof XmlTag tag && localName.equals(tag.getLocalName())) return tag;
            current = current.getParent();
        }
        return null;
    }

    public static XmlTag statementForMethod(XmlFile file, String namespace, String methodName) {
        if (!isWcdkRepositoryFile(file) || namespace == null || methodName == null) return null;
        if (!namespace.equals(attributeValue(file.getRootTag(), "namespace"))) return null;
        for (XmlTag tag : statementTags(file.getRootTag())) {
            if (methodName.equals(attributeValue(tag, "id"))) return tag;
        }
        return null;
    }

    public static List<XmlTag> statementTags(XmlTag root) {
        List<XmlTag> result = new ArrayList<>();
        collectStatementTags(root, result);
        return result;
    }

    private static void collectStatementTags(XmlTag tag, List<XmlTag> result) {
        if (tag == null) return;
        if (tag.getAttribute("id") != null) result.add(tag);
        for (XmlTag child : tag.getSubTags()) collectStatementTags(child, result);
    }

    public static PsiElement findProperty(PsiClass type, String propertyName) {
        if (type == null || propertyName == null || propertyName.isBlank()) return null;
        String name = propertyName.trim();
        PsiVariable field = type.findFieldByName(name, true);
        if (field != null) return field;
        String accessor = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        PsiMethod method = findMethod(type, accessor);
        if (method != null) return method;
        String booleanAccessor = "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return findMethod(type, booleanAccessor);
    }

    public static PsiElement findParameterOrProperty(PsiMethod method, String name) {
        if (method == null || name == null || name.isBlank()) return null;
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            if (name.equals(parameter.getName())) return parameter;
        }
        PsiClass returnClass = method.getReturnType() == null ? null :
                JavaPsiFacade.getInstance(method.getProject()).findClass(
                        method.getReturnType().getCanonicalText(), GlobalSearchScope.projectScope(method.getProject()));
        return findProperty(returnClass, name);
    }
}
