package com.wcdk.r2dbc.idea.reference;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ProcessingContext;
import com.wcdk.r2dbc.idea.util.WcdkXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WcdkXmlReferenceContributor extends PsiReferenceContributor {
    private static final Pattern PARAMETER = Pattern.compile("#\\{\\s*([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*}|:([A-Za-z_$][\\w$]*)");

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class), new AttributeProvider());
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement().withElementType(XmlTokenType.XML_DATA_CHARACTERS), new ParameterProvider());
    }

    private static final class AttributeProvider extends PsiReferenceProvider {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                @NotNull ProcessingContext context) {
            if (!(element instanceof XmlAttributeValue value) || !(value.getParent() instanceof XmlAttribute attribute)) {
                return PsiReference.EMPTY_ARRAY;
            }
            String name = attribute.getLocalName();
            if (!name.equals("namespace") && !name.equals("id") && !name.equals("resultType")
                    && !name.equals("type") && !name.equals("property") && !name.equals("javaType")
                    && !name.equals("resultMap") && !name.equals("parameterType")) {
                return PsiReference.EMPTY_ARRAY;
            }
            return new PsiReference[]{new WcdkAttributeReference(value, name)};
        }
    }

    private static final class WcdkAttributeReference extends PsiReferenceBase<XmlAttributeValue> {
        private final String attributeName;

        private WcdkAttributeReference(XmlAttributeValue element, String attributeName) {
            super(element, true);
            this.attributeName = attributeName;
        }

        @Override
        public PsiElement resolve() {
            XmlTag owner = getElement().getParent().getParent() instanceof XmlTag tag ? tag : null;
            if (owner == null) return null;
            if (attributeName.equals("namespace")) {
                return WcdkXmlUtil.findClass(getElement().getProject(), value());
            }
            String namespace = WcdkXmlUtil.namespace(getElement());
            var repository = WcdkXmlUtil.findClass(getElement().getProject(), namespace);
            if (attributeName.equals("id")) {
                if ("resultMap".equals(owner.getLocalName())
                        || "case".equals(owner.getLocalName())) return null;
                return WcdkXmlUtil.findMethod(repository, value());
            }
            if (attributeName.equals("resultType") || attributeName.equals("type") || attributeName.equals("javaType")) {
                return WcdkXmlUtil.findClass(getElement().getProject(), value());
            }
            if (attributeName.equals("parameterType")) {
                return WcdkXmlUtil.findClass(getElement().getProject(), value());
            }
            if (attributeName.equals("resultMap")) {
                if (!(getElement().getContainingFile() instanceof com.intellij.psi.xml.XmlFile file)) return null;
                return WcdkXmlUtil.findResultMapById(file, value());
            }
            if (attributeName.equals("property")) {
                String resultType = WcdkXmlUtil.attributeValue(owner, "resultType");
                var resultClass = WcdkXmlUtil.findClass(getElement().getProject(), resultType);
                if (resultClass == null) resultClass = WcdkXmlUtil.findClass(getElement().getProject(),
                        WcdkXmlUtil.attributeValue(WcdkXmlUtil.enclosingTag(owner, "resultMap"), "type"));
                return WcdkXmlUtil.findProperty(resultClass, value());
            }
            return null;
        }

        private String value() {
            String text = getElement().getValue();
            return text == null ? "" : text.trim();
        }
    }

    private static final class ParameterProvider extends PsiReferenceProvider {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                @NotNull ProcessingContext context) {
            String text = element.getText();
            Matcher matcher = PARAMETER.matcher(text);
            java.util.List<PsiReference> references = new java.util.ArrayList<>();
            while (matcher.find()) {
                String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                int group = matcher.group(1) != null ? 1 : 2;
                references.add(new WcdkParameterReference(element, matcher.start(group), matcher.end(group), name));
            }
            return references.toArray(PsiReference.EMPTY_ARRAY);
        }
    }

    private static final class WcdkParameterReference extends PsiReferenceBase<PsiElement> {
        private final String name;

        private WcdkParameterReference(PsiElement element, int start, int end, String name) {
            super(element, new com.intellij.openapi.util.TextRange(start, end), false);
            this.name = name;
        }

        @Override
        public PsiElement resolve() {
            XmlTag statement = getElement().getParent() instanceof XmlTag tag ? tag :
                    getElement().getParent() == null ? null : (getElement().getParent().getParent() instanceof XmlTag tag ? tag : null);
            if (statement == null) return null;
            String id = WcdkXmlUtil.attributeValue(statement, "id");
            var repository = WcdkXmlUtil.findClass(getElement().getProject(), WcdkXmlUtil.namespace(getElement()));
            var method = WcdkXmlUtil.findMethod(repository, id);
            String root = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
            PsiElement direct = WcdkXmlUtil.findParameterOrProperty(method, root);
            if (direct != null) return direct;
            return WcdkXmlUtil.findProperty(WcdkXmlUtil.resultClass(getElement().getProject(), statement), root);
        }
    }
}
