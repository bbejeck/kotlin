package org.jetbrains.jet.lang.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lexer.JetTokens;

/**
 * @author max
 */
public class JetArgument extends JetElement {
    public JetArgument(@NotNull ASTNode node) {
        super(node);
    }

    public void accept(JetVisitor visitor) {
        visitor.visitArgument(this);
    }

    @Nullable
    public JetExpression getArgumentExpression() {
        return findChildByClass(JetExpression.class);
    }

    public String getArgumentName() {
        ASTNode firstChildNode = getNode().getFirstChildNode();
        return firstChildNode.getElementType() == JetTokens.IDENTIFIER ? firstChildNode.getText() : null;
    }

    public boolean isNamed() {
        return getArgumentName() != null;
    }

    public boolean isOut() {
        return findChildByType(JetTokens.OUT_KEYWORD) != null;
    }

    public boolean isRef() {
        return findChildByType(JetTokens.REF_KEYWORD) != null;
    }
}