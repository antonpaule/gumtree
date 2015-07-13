/*
 * This file is part of GumTree.
 *
 * GumTree is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GumTree is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GumTree.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2011-2015 Jean-Rémy Falleri <jr.falleri@gmail.com>
 * Copyright 2011-2015 Floréal Morandat <florealm@gmail.com>
 */

package com.github.gumtreediff.io;

import com.github.gumtreediff.actions.model.*;
import com.github.gumtreediff.io.TreeIoUtils.AbstractSerializer;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public final class ActionsIoUtils {

    private ActionsIoUtils() { }

    public static ActionSerializer toText(TreeContext sctx, List<Action> actions,
                                         MappingStore mappings) throws IOException {
        return new ActionSerializer(sctx, mappings, actions) {

            @Override
            protected ActionFormatter newFormatter(TreeContext ctx, Writer writer) throws Exception {
                return new TextFormatter(ctx, writer);
            }
        };
    }

    public static ActionSerializer toXml(TreeContext sctx, List<Action> actions,
                             MappingStore mappings) throws IOException {
        return new ActionSerializer(sctx, mappings, actions) {

            @Override
            protected ActionFormatter newFormatter(TreeContext ctx, Writer writer) throws Exception {
                return new XMLFormatter(ctx, writer);
            }
        };
    }

    abstract static class ActionSerializer extends AbstractSerializer {
        final TreeContext context;
        final MappingStore mappings;
        final List<Action> actions;

        ActionSerializer(TreeContext context, MappingStore mappings, List<Action> actions) {
            this.context = context;
            this.mappings = mappings;
            this.actions = actions;
        }

        protected abstract ActionFormatter newFormatter(TreeContext ctx, Writer writer) throws Exception;

        @Override
        public void writeTo(Writer writer) throws Exception {
            ActionFormatter fmt = newFormatter(context, writer);
            fmt.startActions();
            for (Action a : actions) {
                ITree src = a.getNode();
                if (a instanceof Move) {
                    ITree dst = mappings.getDst(src);
                    fmt.moveAction(src, dst);
                    break;
                } else if (a instanceof Update) {
                    ITree dst = mappings.getDst(src);
                    fmt.updateAction(src, dst);
                    break;
                } else if (a instanceof Insert) {
                    ITree dst = a.getNode();
                    if (dst.isRoot())
                        fmt.insertRoot(src);
                    else
                        fmt.insertAction(src, dst.getParent(), dst.getParent().getChildPosition(dst));
                } else if (a instanceof Delete) {
                    fmt.deleteAction(src);
                }
            }
            fmt.endActions();
        }
    }

    interface ActionFormatter {
        void startActions() throws Exception;
        void insertRoot(ITree node) throws Exception;
        void insertAction(ITree node, ITree parent, int index) throws Exception;
        void moveAction(ITree src, ITree dst) throws Exception;
        void updateAction(ITree src, ITree dst) throws Exception;
        void deleteAction(ITree node) throws Exception;
        void endActions() throws Exception;
    }

    static class XMLFormatter implements ActionFormatter {
        final TreeContext context;
        final XMLStreamWriter writer;

        XMLFormatter(TreeContext context, Writer w) throws XMLStreamException {
            XMLOutputFactory f = XMLOutputFactory.newInstance();
            writer = new IndentingXMLStreamWriter(f.createXMLStreamWriter(w));
            this.context = context;
        }


        @Override
        public void startActions() throws XMLStreamException {
            writer.writeStartDocument();
            writer.writeStartElement("actions");
        }

        @Override
        public void insertRoot(ITree node) throws Exception {
            start(Insert.class, node);
            end(node);
        }

        @Override
        public void insertAction(ITree node, ITree parent, int index) throws Exception {
            start(Insert.class, node);
            writer.writeAttribute("parent", Integer.toString(parent.getId()));
            writer.writeAttribute("at", Integer.toString(index));
            end(node);
        }

        @Override
        public void moveAction(ITree src, ITree dst) throws XMLStreamException {
            start(Move.class, src);
            writer.writeAttribute("to", Integer.toString(dst.getId()));
            end(src);
        }

        @Override
        public void updateAction(ITree src, ITree dst) throws XMLStreamException {
            start(Update.class, src);
            writer.writeAttribute("to", Integer.toString(dst.getId()));
            end(src);
        }

        @Override
        public void deleteAction(ITree node) throws Exception {
            start(Delete.class, node);
            end(node);
        }


        @Override
        public void endActions() throws XMLStreamException {
            writer.writeEndElement();
            writer.writeEndDocument();
        }

        private void start(Class<? extends Action> name, ITree src) throws XMLStreamException {
            writer.writeEmptyElement(name.getSimpleName().toLowerCase());
            writer.writeAttribute("tree", Integer.toString(src.getId()));
        }

        private void end(ITree node) throws XMLStreamException {
//            writer.writeEndElement();
        }
    }

    static class TextFormatter implements ActionFormatter {
        final Writer writer;
        final TreeContext context;
        public TextFormatter(TreeContext ctx, Writer writer) {
            this.context = ctx;
            this.writer = writer;
        }


        @Override
        public void startActions() throws Exception { }

        @Override
        public void insertRoot(ITree node) throws Exception {
            write("Insert root %s", _(node));
        }

        @Override
        public void insertAction(ITree node, ITree parent, int index) throws Exception {
            write("Insert %s -> %d %s",  _(node), index,  _(parent));
        }

        @Override
        public void moveAction(ITree src, ITree dst) throws Exception {
            write("Move %s -> %s", _(src), _(dst));
        }

        @Override
        public void updateAction(ITree src, ITree dst) throws Exception {
            write("Move %s -> %s",  _(src),  _(dst));
        }

        @Override
        public void deleteAction(ITree node) throws Exception {
            write("Delete %s", _(node));
        }

        @Override
        public void endActions() throws Exception { }

        private void write(String fmt, Object... objs) throws IOException {
            writer.append(String.format(fmt, objs));
            writer.append("\n");
        }

        private String _(ITree node) {
            return String.format("%s(%d)", node.toPrettyString(context), node.getId());
        }
    }
}
