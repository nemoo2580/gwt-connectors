package pl.tecna.gwt.connectors.client.elements;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import pl.tecna.gwt.connectors.client.ConnectionPoint;
import pl.tecna.gwt.connectors.client.Diagram;
import pl.tecna.gwt.connectors.client.Point;
import pl.tecna.gwt.connectors.client.drop.DiagramWidgetDropController;
import pl.tecna.gwt.connectors.client.listeners.event.DiagramAddEvent;
import pl.tecna.gwt.connectors.client.listeners.event.DiagramRemoveEvent;
import pl.tecna.gwt.connectors.client.util.ConnectorStyle;
import pl.tecna.gwt.connectors.client.util.ConnectorsClientBundle;

import com.allen_sauer.gwt.dnd.client.util.WidgetLocation;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.Widget;

/**
 * @author Kamil Kurek
 */
public class Shape extends FocusPanel implements Element {
 
  /**
   * Enum with values defining Shape object connection points positioning type
   */
  public enum CPShapeType {
    OVAL, DIAMOND, RECTANGLE, USER_DEFINED
  }

  public static final int END_POINTS_VIS_DELAY = 1000;

  private Logger LOG = Logger.getLogger("Shape");

  public List<EndPoint> endPoints;

  public CPShapeType cpShapeType;

  public Widget connectedWidget;

  public List<ConnectionPoint> connectionPoints;

  public AbsolutePanel connectionPointsPanel;

  public DiagramWidgetDropController shapeDropController;

  private boolean enableOverlap = false;

  public Diagram diagram;
  
  public boolean startPointsVisible = false;

  public int left = 0;
  public int top = 0;

  /**
   * Defines size of connection point margin
   */
  public static int CP_MARGIN = 7;
  
  public static int CP_PADDING = 3;
  
  /**
   * Defines tolerance in merging sections
   */
  public static int SECTION_TOLERANCE = 20;
  public int offsetTop = 0;
  public int offsetLeft = 0;

  /**
   * Defines translation
   */
  public int translationX = 0;
  public int translationY = 0;

  private Timer endPointsShowTimer;
  private boolean connectable = false;

  private HandlerRegistration showConnStartMouseHandler;
  private HandlerRegistration hideConnStartMouseHandler;

  public ConnectorStyle connectorsStyle = ConnectorStyle.SOLID;

  public Shape(Widget w) {

    this(w, CPShapeType.RECTANGLE);
  }

  /**
   * Shape constructor
   * 
   * @param w Widget to which the Shape is connected
   * @param cpShapeType defines how connection points will be placed, when set to USER_DEFINED You
   *          have to override createUserDefinedShapeCP method
   */
  public Shape(Widget w, CPShapeType cpShapeType) {
    this.endPoints = new ArrayList<EndPoint>();
    this.connectedWidget = w;
    this.cpShapeType = cpShapeType;
    this.setStylePrimaryName(ConnectorsClientBundle.INSTANCE.css().shapeUnselected());
  }

  public void showOnDiagram(final Diagram diagram) {

    this.diagram = diagram;

    sinkEvents(Event.ONDBLCLICK);

    // Add Shape to Diagram
    diagram.shapes.add(this);

    // Add shape to parent panel
    this.setPixelSize(connectedWidget.getOffsetWidth() + CP_MARGIN * 2 + offsetLeft, connectedWidget.getOffsetHeight()
        + CP_MARGIN * 2 + offsetTop);

    ((AbsolutePanel) connectedWidget.getParent()).add(this, connectedWidget.getAbsoluteLeft()
        - connectedWidget.getParent().getAbsoluteLeft() - CP_MARGIN - offsetLeft - CP_PADDING, connectedWidget
        .getAbsoluteTop()
        - connectedWidget.getParent().getAbsoluteTop() - CP_MARGIN - offsetTop - CP_PADDING);

    // Add Absolute Panel which contains Widget in the center and ConnectionPoints on North, East,
    // South, and West
    this.left =
        connectedWidget.getAbsoluteLeft() - connectedWidget.getParent().getAbsoluteLeft() - CP_MARGIN - offsetLeft;
    this.top = connectedWidget.getAbsoluteTop() - connectedWidget.getParent().getAbsoluteTop() - CP_MARGIN - offsetTop;

    connectionPointsPanel = new AbsolutePanel();

    this.add(connectionPointsPanel);

    connectionPointsPanel.setPixelSize(connectedWidget.getOffsetWidth() + CP_MARGIN * 2 + offsetLeft, connectedWidget
        .getOffsetHeight()
        + CP_MARGIN * 2 + offsetTop);
    connectionPointsPanel.add(connectedWidget, CP_MARGIN + offsetLeft, CP_MARGIN + offsetTop);

    // Add connection points to the absolute panel
    switch (cpShapeType) {
      case OVAL:
        connectionPoints = createOvalShapeCP(connectionPointsPanel, diagram);
        break;
      case DIAMOND:
        connectionPoints = createDiamondShapeCP(connectionPointsPanel, diagram);
        break;
      case RECTANGLE:
        connectionPoints = createRectangleShapeCP(connectionPointsPanel, diagram);
        break;
      case USER_DEFINED:
        connectionPoints = createUserDefinedShapeCP(connectionPointsPanel, diagram);
        if (connectionPoints == null) {
          connectionPoints = createRectangleShapeCP(connectionPointsPanel, diagram);
        }
        break;
    }

    // Make Focus Panel draggable
    diagram.shapeDragController.makeDraggable(this);

    // Create drop controller
    shapeDropController = new DiagramWidgetDropController(this);

    diagram.endPointDragController.registerDropController(shapeDropController);
    diagram.shapeDragController.registerDropController(shapeDropController);

    sinkEvents(Event.ONMOUSEUP);
    sinkEvents(Event.ONCLICK);
    sinkEvents(Event.ONMOUSEDOWN);

    int endX = diagram.boundaryPanel.getWidgetLeft(this) - diagram.boundaryPanel.getAbsoluteLeft();
    int endY = diagram.boundaryPanel.getWidgetTop(this) - diagram.boundaryPanel.getAbsoluteTop();
    diagram.onDiagramAdd(new DiagramAddEvent(this, endX, endY));
  }
  
  public List<ConnectionPoint> repaint(Diagram diagram) {
    return repaint(diagram, cpShapeType);
  }

  public List<ConnectionPoint> repaint(Diagram diagram, CPShapeType newShapeType) {
    LOG.info("REPAINT (new shape type: " + newShapeType + " old shape type: " + cpShapeType + ")");
    this.setPixelSize(connectedWidget.getOffsetWidth() + CP_MARGIN * 2 + offsetLeft, connectedWidget.getOffsetHeight()
        + CP_MARGIN * 2 + offsetTop);

    connectionPointsPanel.setPixelSize(connectedWidget.getOffsetWidth() + CP_MARGIN * 2 + offsetLeft, connectedWidget
        .getOffsetHeight()
        + CP_MARGIN * 2 + offsetTop);

    List<EndPoint> connectedEndPoints = null;
    if (newShapeType != cpShapeType) {
      connectedEndPoints = new LinkedList<EndPoint>();
      for (ConnectionPoint cp : connectionPoints) {
        connectedEndPoints.addAll(cp.gluedEndPoints);
      }
      updateNumberOfCP(newShapeType, cpShapeType);
    }
    
    switch (newShapeType) {
      case OVAL:
        calculateOvalCPPositions(connectionPoints, connectionPointsPanel);
        break;
      case DIAMOND:
        calculateDiamondShapeCP(connectionPoints, connectionPointsPanel);
        break;
      case RECTANGLE:
        calculateRectangleCPPositions(connectionPoints, connectionPointsPanel);
        break;
      case USER_DEFINED:
        //TODO calculate user defined CPPositions
        break;
    }

    updateConnectionPointsPositions(connectionPoints, connectionPointsPanel);
    cpShapeType = newShapeType;
    
    if (connectedEndPoints != null) {
      reconnectEndPoints(connectedEndPoints, connectionPoints);
    }
    
    return connectionPoints;
  }
  
  private void updateNumberOfCP(CPShapeType newShapeType, CPShapeType oldShapeType) {
    LOG.info("UPDATE number of connection points (current number:" + connectionPoints.size() + " shape type: " + newShapeType + ")");
    List<ConnectionPoint> toRemove = new ArrayList<ConnectionPoint>();
    int newCpSize = 8;
    switch (newShapeType) {
      case DIAMOND: 
      case OVAL: {
        newCpSize = 8;
      } break;
      case RECTANGLE: {
        newCpSize = 12;
      } break;
      case USER_DEFINED: {
        //TODO update connection points for user defined shape type
      }
    }
    
    if (connectionPoints.size() > newCpSize) {
      for (int i = newCpSize ; i < connectionPoints.size() ; i++) {
        toRemove.add(connectionPoints.get(i));
      }
    } else if (connectionPoints.size() < newCpSize) {
      for (int i = connectionPoints.size() ; i < newCpSize ; i++) {
        ConnectionPoint cp = new ConnectionPoint(ConnectionPoint.DIRECTION_LEFT, i, connectedWidget);
        connectionPoints.add(cp);
        connectionPointsPanel.add(cp);
      }
    }
    
    LOG.fine("Number of removed connection points: " + toRemove.size());
    if (newCpSize - connectionPoints.size() > 0) {
      LOG.fine("Number of added connection points: " + (connectionPoints.size() - newCpSize));
    }
    
    connectionPoints.removeAll(toRemove);
    
    updateCPDirections(connectionPoints, newShapeType);
  }
  
  private void updateCPDirections(List<ConnectionPoint> connectionPoints, CPShapeType shapeType) {
    switch (shapeType) {
      case DIAMOND:
      case OVAL: {
        connectionPoints.get(0).connectionDirection = ConnectionPoint.DIRECTION_LEFT;
        connectionPoints.get(1).connectionDirection = ConnectionPoint.DIRECTION_TOP;
        connectionPoints.get(2).connectionDirection = ConnectionPoint.DIRECTION_TOP;
        connectionPoints.get(3).connectionDirection = ConnectionPoint.DIRECTION_TOP;
        connectionPoints.get(4).connectionDirection = ConnectionPoint.DIRECTION_RIGHT;
        connectionPoints.get(5).connectionDirection = ConnectionPoint.DIRECTION_BOTTOM;
        connectionPoints.get(6).connectionDirection = ConnectionPoint.DIRECTION_BOTTOM;
        connectionPoints.get(7).connectionDirection = ConnectionPoint.DIRECTION_BOTTOM;
      } break;
      case RECTANGLE: {
        connectionPoints.get(0).connectionDirection = ConnectionPoint.DIRECTION_TOP;
        connectionPoints.get(1).connectionDirection = ConnectionPoint.DIRECTION_TOP;
        connectionPoints.get(2).connectionDirection = ConnectionPoint.DIRECTION_TOP;
        connectionPoints.get(3).connectionDirection = ConnectionPoint.DIRECTION_RIGHT;
        connectionPoints.get(4).connectionDirection = ConnectionPoint.DIRECTION_RIGHT;
        connectionPoints.get(5).connectionDirection = ConnectionPoint.DIRECTION_RIGHT;
        connectionPoints.get(6).connectionDirection = ConnectionPoint.DIRECTION_BOTTOM;
        connectionPoints.get(7).connectionDirection = ConnectionPoint.DIRECTION_BOTTOM;
        connectionPoints.get(8).connectionDirection = ConnectionPoint.DIRECTION_BOTTOM;
        connectionPoints.get(9).connectionDirection = ConnectionPoint.DIRECTION_LEFT;
        connectionPoints.get(10).connectionDirection = ConnectionPoint.DIRECTION_LEFT;
        connectionPoints.get(11).connectionDirection = ConnectionPoint.DIRECTION_LEFT;
      } break;
      case USER_DEFINED: {
        //TODO user defined update connection direction
      } break;
    }
  }

  /**
   * Removes Shape from Diagram and from its boundaryPanel
   * 
   * @param diagram a Diagram the Shape will be removed from
   */
  public void removeFromDiagram(Diagram diagram) {
    removeFromDiagram(diagram, true);
  }

  /**
   * Removes Shape from Diagram and from its boundaryPanel
   * 
   * @param diagram a Diagram the Shape will be removed from
   */
  public void removeFromDiagram(Diagram diagram, boolean fireEvent) {
    try {

      if (fireEvent) {
        int removedX = diagram.boundaryPanel.getWidgetLeft(this) - diagram.boundaryPanel.getAbsoluteLeft();
        int removedY = diagram.boundaryPanel.getWidgetTop(this) - diagram.boundaryPanel.getAbsoluteTop();
        diagram.onDiagramRemove(new DiagramRemoveEvent(this, removedX, removedY));
      }

      enableConnectionCreate(false);

      // Remove Shape from Diagram
      diagram.endPointDragController.unregisterDropController(shapeDropController);
      diagram.shapeDragController.unregisterDropController(shapeDropController);
      diagram.shapeDragController.makeNotDraggable(this);

      diagram.shapes.remove(this);
      diagram.boundaryPanel.remove(this);

      List<Connector> connectors = getConnectedConnectors();
      for (Connector conn : connectors) {
        conn.removeFromDiagram(diagram, false);
      }

    } catch (Exception e) {
      LOG.log(Level.SEVERE, "Remove from diagram exception", e);
    }
  }

  public ConnectionPoint findNearestConnectionPoint(int absLeft, int absTop) {
    return findNearestConnectionPoint(absLeft, absTop, new ArrayList<ConnectionPoint>());
  }

  public ConnectionPoint findNearestConnectionPoint(int absLeft, int absTop, List<ConnectionPoint> excluded) {

    if (excluded == null) {
      excluded = new ArrayList<ConnectionPoint>();
    }
    ConnectionPoint retCP = null;
    int distance = Integer.MAX_VALUE;
    for (ConnectionPoint cp : connectionPoints) {
      int tempDist = Math.abs(absLeft - cp.getCenterLeft()) + Math.abs(absTop - cp.getCenterTop());
      if (tempDist < distance && !excluded.contains(cp)) {
        distance = tempDist;
        retCP = cp;
      }
    }

    return retCP;
  }

  public ConnectionPoint findNearestFreeConnectionPoint(int absLeft, int absTop) {
    List<ConnectionPoint> excluded = new ArrayList<ConnectionPoint>();
    for (ConnectionPoint cp : connectionPoints) {
      if (cp.gluedEndPoints != null && cp.gluedEndPoints.size() != 0) {
        excluded.add(cp);
      }
    }
    return findNearestConnectionPoint(absLeft, absTop, excluded);
  }

  private List<ConnectionPoint> createRectangleShapeCP(AbsolutePanel connectionPointsPanel, Diagram diagram) {
    List<ConnectionPoint> connectionPoints = new ArrayList<ConnectionPoint>();

    ConnectionPoint cp = new ConnectionPoint(ConnectionPoint.DIRECTION_TOP, 0, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_TOP, 1, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_TOP, 2, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_RIGHT, 3, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_RIGHT, 4, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_RIGHT, 5, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_BOTTOM, 6, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_BOTTOM, 7, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_BOTTOM, 8, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_LEFT, 9, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_LEFT, 10, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_LEFT, 11, connectedWidget);
    connectionPoints.add(cp);

    calculateRectangleCPPositions(connectionPoints, connectionPointsPanel);
    
    addConnectionPoints(connectionPoints, connectionPointsPanel);
    
    return connectionPoints;
  }
  
  private void calculateRectangleCPPositions(List<ConnectionPoint> connectionPoints, AbsolutePanel connectionPointsPanel) {
    LOG.info("Calculate rectangle connection points positions");
    
    int cpPanelHeight = connectionPointsPanel.getOffsetHeight();
    int cpPanelWidth = connectionPointsPanel.getOffsetWidth();
    
    connectionPoints.get(0).positionOnCPPanel = new Point((cpPanelWidth / 2) - (ConnectionPoint.CPSize / 2)
        - (cpPanelWidth / 4), 0);
    connectionPoints.get(1).positionOnCPPanel = new Point((cpPanelWidth / 2) - (ConnectionPoint.CPSize / 2), 0);
    connectionPoints.get(2).positionOnCPPanel = new Point((cpPanelWidth / 2) - (ConnectionPoint.CPSize / 2)
        + (cpPanelWidth / 4), 0);
    connectionPoints.get(3).positionOnCPPanel = new Point(cpPanelWidth - ConnectionPoint.CPSize, (cpPanelHeight / 2)
        - (ConnectionPoint.CPSize / 2) - (cpPanelHeight / 4));
    connectionPoints.get(4).positionOnCPPanel = new Point(cpPanelWidth - ConnectionPoint.CPSize, (cpPanelHeight / 2)
        - (ConnectionPoint.CPSize / 2));
    connectionPoints.get(5).positionOnCPPanel = new Point(cpPanelWidth - ConnectionPoint.CPSize, (cpPanelHeight / 2)
        - (ConnectionPoint.CPSize / 2) + (cpPanelHeight / 4));
    connectionPoints.get(6).positionOnCPPanel = new Point((cpPanelWidth / 2) - (ConnectionPoint.CPSize / 2)
        + (cpPanelWidth / 4), cpPanelHeight - ConnectionPoint.CPSize);
    connectionPoints.get(7).positionOnCPPanel = new Point((cpPanelWidth / 2) - (ConnectionPoint.CPSize / 2), cpPanelHeight
        - ConnectionPoint.CPSize);
    connectionPoints.get(8).positionOnCPPanel = new Point((cpPanelWidth / 2) - (ConnectionPoint.CPSize / 2)
        - (cpPanelWidth / 4), cpPanelHeight - ConnectionPoint.CPSize);
    connectionPoints.get(9).positionOnCPPanel = new Point(0, (cpPanelHeight / 2) - ConnectionPoint.CPSize / 2
        + (cpPanelHeight / 4));
    connectionPoints.get(10).positionOnCPPanel = new Point(0, (cpPanelHeight / 2) - ConnectionPoint.CPSize / 2);
    connectionPoints.get(11).positionOnCPPanel = new Point(0, (cpPanelHeight / 2) - ConnectionPoint.CPSize / 2
        - (cpPanelHeight / 4));
    
  }
  
  private List<ConnectionPoint> createOvalShapeCP(AbsolutePanel connectionPointsPanel, Diagram diagram) {
    List<ConnectionPoint> connectionPoints = new ArrayList<ConnectionPoint>();

    ConnectionPoint cp = new ConnectionPoint(ConnectionPoint.DIRECTION_LEFT, 0, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_TOP, 1, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_TOP, 2, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_TOP, 3, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_RIGHT, 4, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_BOTTOM, 5, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_BOTTOM, 6, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_BOTTOM, 7, connectedWidget);
    connectionPoints.add(cp);

    calculateOvalCPPositions(connectionPoints, connectionPointsPanel);
    
    addConnectionPoints(connectionPoints, connectionPointsPanel);

    return connectionPoints;
  }
  
  private void calculateOvalCPPositions(List<ConnectionPoint> connectionPoints, AbsolutePanel connectionPointsPanel) {
    LOG.info("Calculate oval connection points positions");
    int cpPanelHeight = (int) Math.floor(connectedWidget.getOffsetHeight() + CP_MARGIN * 2 - ConnectionPoint.CPSize);
    int cpPanelWidth = (int) Math.floor(connectedWidget.getOffsetWidth() + CP_MARGIN * 2 - ConnectionPoint.CPSize);

    double centerLeft = Math.floor((double) cpPanelWidth / 2.0) + offsetLeft;
    double centerTop = Math.floor((double) cpPanelHeight / 2.0) + offsetTop;

    for (int i = 0; i < 8; i++) {
      connectionPoints.get(i).positionOnCPPanel = new Point( 
          (int) Math.ceil(centerLeft - (((double) cpPanelWidth / 2.0) * Math.cos((double) 2.0 * Math.PI / (double) 8.0 * i))), 
          (int) Math.ceil(centerTop - (((double) cpPanelHeight / 2.0) * Math.sin((double) 2.0 * Math.PI / (double) 8.0 * i))));
      
      
      if (i == 0 || i == 4) {
        connectionPoints.get(i).positionOnCPPanel.setTopPosition(connectionPoints.get(i).positionOnCPPanel.getTop() + 1);
      } else if (i == 2) {
        connectionPoints.get(i).positionOnCPPanel.setLeftPosition(connectionPoints.get(i).positionOnCPPanel.getLeft() + 1);
      }
    }
  }

  private List<ConnectionPoint> createDiamondShapeCP(AbsolutePanel connectionPointsPanel, Diagram diagram) {
    List<ConnectionPoint> connectionPoints = new ArrayList<ConnectionPoint>();

    ConnectionPoint cp = new ConnectionPoint(ConnectionPoint.DIRECTION_LEFT, 0, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_TOP, 1, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_TOP, 2, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_TOP, 3, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_RIGHT, 4, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_BOTTOM, 5, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_BOTTOM, 6, connectedWidget);
    connectionPoints.add(cp);
    cp = new ConnectionPoint(ConnectionPoint.DIRECTION_BOTTOM, 7, connectedWidget);
    connectionPoints.add(cp);

    calculateDiamondShapeCP(connectionPoints, connectionPointsPanel);
    
    addConnectionPoints(connectionPoints, connectionPointsPanel);

    return connectionPoints;
  }
  
  private void calculateDiamondShapeCP(List<ConnectionPoint> connectionPoints, AbsolutePanel connectionPointsPanel) {
    LOG.info("Calculate diamond connection points positions");
    int cpPanelHeight = connectionPointsPanel.getOffsetHeight();
    int cpPanelWidth = connectionPointsPanel.getOffsetWidth();
    int horizontalDifference = ((cpPanelWidth / 2) - ConnectionPoint.CPSize / 2) / 2;
    int verticalDifference = ((cpPanelHeight / 2) - ((ConnectionPoint.CPSize) / 2)) / 2;

    connectionPoints.get(0).positionOnCPPanel = new Point(0, (cpPanelHeight / 2) - ((ConnectionPoint.CPSize) / 2));
    connectionPoints.get(1).positionOnCPPanel = new Point(horizontalDifference, (cpPanelHeight / 2)
        - ((ConnectionPoint.CPSize) / 2) - verticalDifference);
    connectionPoints.get(2).positionOnCPPanel = new Point((cpPanelWidth / 2) - ConnectionPoint.CPSize / 2, 0);
    connectionPoints.get(3).positionOnCPPanel = new Point((cpPanelWidth / 2) - ConnectionPoint.CPSize / 2
        + horizontalDifference, 0 + verticalDifference);
    connectionPoints.get(4).positionOnCPPanel = new Point(cpPanelWidth - ConnectionPoint.CPSize, (cpPanelHeight / 2)
        - (ConnectionPoint.CPSize / 2));
    connectionPoints.get(5).positionOnCPPanel = new Point(cpPanelWidth - ConnectionPoint.CPSize - horizontalDifference,
        (cpPanelHeight / 2) - (ConnectionPoint.CPSize / 2) + verticalDifference);
    connectionPoints.get(6).positionOnCPPanel = new Point((cpPanelWidth / 2) - (ConnectionPoint.CPSize / 2), cpPanelHeight
        - ConnectionPoint.CPSize);
    connectionPoints.get(7).positionOnCPPanel = new Point((cpPanelWidth / 2) - (ConnectionPoint.CPSize / 2)
        - horizontalDifference, cpPanelHeight - ConnectionPoint.CPSize - verticalDifference);
  }

  private List<ConnectionPoint> createUserDefinedShapeCP(AbsolutePanel connectionPointsPanel, Diagram diagram) {
    return null;
  }

  private void refreshUserDefinedCPPositions(AbsolutePanel connectionPointsPanel, Diagram diagram) {
  }

  private void addConnectionPoints(List<ConnectionPoint> connectionPoints, AbsolutePanel connectionPointsPanel) {
    for (ConnectionPoint cp : connectionPoints) {
      connectionPointsPanel.add(cp, cp.positionOnCPPanel.getLeft(), cp.positionOnCPPanel.getTop());
      cp.showOnDiagram(diagram);
    }
  }
  
  private void updateConnectionPointsPositions(List<ConnectionPoint> connectionPoints, AbsolutePanel connectionPointsPanel) {
    for (ConnectionPoint cp : connectionPoints) {
      connectionPointsPanel.setWidgetPosition(cp, cp.positionOnCPPanel.getLeft(), cp.positionOnCPPanel.getTop());
    }
  }
  
  public void hideConnectionPoints(Diagram diagram) {
    for (int i = 0; i < connectionPoints.size(); i++) {
      connectionPoints.get(i).setUnfocused();
    }
  }

  public void showConnectionPoints(Diagram diagram) {
    for (int i = 0; i < connectionPoints.size(); i++) {
      connectionPoints.get(i).setSelected();
    }
  }

  public ConnectionPoint getCPForPosition(int cpPos) {
    for (ConnectionPoint cp : connectionPoints) {
      if (cp.index == cpPos) {
        return cp;
      }
    }
    return null;
  }

  /**
   * Returns shape left position on parent panel
   * 
   * @return widget left position on parent panel {@link AbsolutePanel}
   */
  public int getRelativeShapeLeft() {
    if (this.diagram != null) {
      return new WidgetLocation(this, diagram.boundaryPanel).getLeft();
    } else {
      LOG.severe("getRelativeShapeLeft -> -1");
      return -1;
    }
  }

  /**
   * Returns shape top position on parent panel
   * 
   * @return widget top position on parent panel {@link AbsolutePanel}
   */
  public int getRelativeShapeTop() {
    if (this.diagram != null) {
      return new WidgetLocation(this, diagram.boundaryPanel).getTop();
    } else {
      LOG.severe("getRelativeShapeLeft -> -1");
      return -1;
    }
  }

  public Point getCPPosition(ConnectionPoint cp) {
    Point point = null;

    if (connectionPoints.contains(cp)) {
      if (this.getParent() != null) {
        int left = cp.getAbsoluteLeft() - this.diagram.boundaryPanel.getAbsoluteLeft();
        int top = cp.getAbsoluteTop() - this.diagram.boundaryPanel.getAbsoluteTop();
        point = new Point(left, top);
        return point;
      }
    }
    return null;
  }

  public List<Connector> getConnectedConnectors() {
    List<Connector> connectors = new ArrayList<Connector>();

    for (ConnectionPoint cp : connectionPoints) {
      for (EndPoint endPoint : cp.gluedEndPoints) {
        connectors.add(endPoint.connector);
      }
    }

    return connectors;
  }

  /**
   * Update sections connected to the Shape (should be used after Shape position changed)
   */
  public void updateConnectors() {
    for (ConnectionPoint cp : connectionPoints) {
      for (EndPoint ep : cp.gluedEndPoints) {
        boolean vertical = false;
        if (ep.connector.prevSectionForPoint(ep) != null) {
          vertical = ep.connector.prevSectionForPoint(ep).isVertical();
        } else {
          vertical = ep.connector.nextSectionForPoint(ep).isVertical();
        }
        ep.setPosition(cp.getCenterLeft(), cp.getCenterTop());
        if (vertical) {
          ep.updateOpositeEndPointOfVerticalSection();
        } else {
          ep.updateOpositeEndPointOfHorizontalSection();
        }
      }
    }
  }

  /**
   * Gives connector's sections that lay on this {@link Shape}
   * 
   * @param connector {@link Connector}
   * @return list of overlap sections
   */
  public List<Section> overlapSections(Connector connector) {
    List<Section> sections = new ArrayList<Section>();

    for (int i = 0; i < connector.sections.size(); i++) {
      if ((connector.sections.get(i).connector.endEndPoint.isGluedToConnectionPoint() && connector.sections.get(i).connector.endEndPoint.gluedConnectionPoint
          .getParentShape().equals(this))
          || (connector.sections.get(i).connector.startEndPoint.isGluedToConnectionPoint() && connector.sections.get(i).connector.startEndPoint.gluedConnectionPoint
              .getParentShape().equals(this))) {
      } else if (isOnThisShape(connector.sections.get(i))) {
        sections.add(connector.sections.get(i));
      }
    }
    return sections;
  }

  public boolean goesThroughThisShape(Section sect) {
    boolean ret = true;
    LOG.info("Assuming, that section goes through this shape");
    return ret;
  }

  /**
   * Determines whether given {@link Section} is on this Shape's containing widget
   * 
   * @return <code>true</code>, if section is on current shape
   */
  public boolean isOnThisShape(Section section) {
    Point startPoint = section.startPoint;
    Point endPoint = section.endPoint;

    int direction;
    if (section.isHorizontal()) {
      direction = Section.HORIZONTAL;
    } else if (section.isVertical()) {
      direction = Section.VERTICAL;
    } else {
      LOG.severe("isOnThisShape -> direction = -1");
      direction = -1;
    }

    if (this.getParent() != null) {

      int shapeLeft = this.getRelativeShapeLeft();
      int shapeTop = this.getRelativeShapeTop();
      int shapeRight = shapeLeft + this.getOffsetWidth();
      int shapeBottom = shapeTop + this.getOffsetHeight();

      if (direction == Section.HORIZONTAL) {
        if (shapeTop <= startPoint.getTop() && shapeBottom >= startPoint.getTop()) {
          if ((startPoint.getLeft() >= shapeLeft && startPoint.getLeft() <= shapeRight)
              || (endPoint.getLeft() >= shapeLeft && endPoint.getLeft() <= shapeRight)
              || (startPoint.getLeft() <= shapeLeft && endPoint.getLeft() >= shapeRight)
              || (startPoint.getLeft() >= shapeLeft && endPoint.getLeft() <= shapeRight)) {
            return true;
          }
        }
      } else if (direction == Section.VERTICAL) {
        if (shapeLeft <= startPoint.getLeft() && shapeRight >= startPoint.getLeft()) {
          if ((startPoint.getTop() >= shapeTop && startPoint.getTop() <= shapeBottom)
              || (endPoint.getTop() >= shapeTop && endPoint.getTop() <= shapeBottom)
              || (startPoint.getTop() <= shapeTop && endPoint.getTop() >= shapeBottom)
              || (startPoint.getTop() >= shapeTop && endPoint.getTop() <= shapeBottom)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  /**
   * Define wheather given x and y coordinates lie on this {@link Shape}
   * 
   * @param x
   * @param y
   * @return <code>true</code>, if the point is on the shape
   */
  public boolean isOnShape(int x, int y) {
    if (this.getParent() != null) {
      int shapeLeft = this.getRelativeShapeLeft();
      int shapeTop = this.getRelativeShapeTop();
      int shapeRight = shapeLeft + this.getOffsetWidth();
      int shapeBottom = shapeTop + this.getOffsetHeight();
      if ((shapeLeft <= x && shapeRight >= x) && (shapeTop <= y && shapeBottom >= y)) {
        return true;
      }
    }
    return false;
  }

  public int getConnectedWidgetLeft() {
    if (this.isAttached()) {
      AbsolutePanel boundary = (AbsolutePanel) this.getParent();
      return connectedWidget.getAbsoluteLeft() - boundary.getAbsoluteLeft();
    } else {
      return -1;
    }
  }

  public int getConnectedWidgetTop() {
    if (this.isAttached()) {
      AbsolutePanel boundary = (AbsolutePanel) this.getParent();
      return connectedWidget.getAbsoluteTop() - boundary.getAbsoluteTop();
    } else {
      return -1;
    }
  }

  /**
   * Defines, wheather this {@link Shape} lie on selected rectangular
   * 
   * @param startSelectionPoint
   * @param endSelectionPoint
   * @return <code>true</code>, if the shape is on defined rectangle
   */
  public boolean isInRect(Point startSelectionPoint, Point endSelectionPoint) {
    boolean xBetween = false;
    boolean yBetween = false;
    xBetween =
        isNumberBetween(startSelectionPoint.getLeft(), endSelectionPoint.getLeft(), getRelativeShapeLeft()
            + getOffsetWidth() / 2);
    yBetween =
        isNumberBetween(startSelectionPoint.getTop(), endSelectionPoint.getTop(), getRelativeShapeTop()
            + getOffsetHeight() / 2);
    return (xBetween && yBetween);
  }

  private boolean isNumberBetween(int bound1, int bound2, int q) {
    int min = 0;
    int max = 0;

    if (bound1 <= bound2) {
      min = bound1;
      max = bound2;
    } else {
      min = bound2;
      max = bound1;
    }

    if (q > min && q < max) {
      return true;
    }

    // Log.info(q+" is not between "+min+"-"+max);
    return false;
  }

  public int getTranslationX() {
    return translationX;
  }

  public void setTranslationX(int translationX) {
    this.translationX = translationX;
  }

  public int getTranslationY() {
    return translationY;
  }

  public void setTranslationY(int translationY) {
    this.translationY = translationY;
  }

  public void setEnableOverlap(boolean enableOverlap) {
    this.enableOverlap = enableOverlap;
  }

  public boolean isEnableOverlap() {
    return enableOverlap;
  }

  public void hideShapeConnectorStartPionts() {
    startPointsVisible = false;
    if (!endPoints.isEmpty()) {
      for (EndPoint ep : endPoints) {
        ep.removeFromParent();
        diagram.endPointDragController.makeNotDraggable(ep);
      }
      endPoints.clear();
    }
  }

  public void showShapeConnectorStartPoints() {
    startPointsVisible = true;
    endPointsShowTimer.cancel();
    if (endPoints.isEmpty()) {
      AbsolutePanel boundaryPanel = diagram.boundaryPanel;
      for (ConnectionPoint cp : connectionPoints) {
        if (cp.gluedEndPoints.size() == 0) {
          Point cpPoint = getCPPosition(cp);
          ShapeConnectorStart ep =
              new ShapeConnectorStart(cpPoint.getLeft(), cpPoint.getTop(), Shape.this, endPointsShowTimer, cp);
          boundaryPanel.add(ep, (int) cpPoint.getLeft(), (int) cpPoint.getTop());
          diagram.endPointDragController.makeDraggable(ep);
          endPoints.add(ep);
        }
      }
    }
  }

  /**
   * Controls showing end points for creating connection
   * 
   * @param enable
   */
  public void enableConnectionCreate(boolean enable) {
    if (enable) {
      if (showConnStartMouseHandler == null && hideConnStartMouseHandler == null) {
        if (endPointsShowTimer == null) {
          endPointsShowTimer = new Timer() {

            @Override
            public void run() {
              hideShapeConnectorStartPionts();
            }
          };
        }

        MouseOverHandler showConnStartMouseOver = new MouseOverHandler() {

          public void onMouseOver(MouseOverEvent event) {
            showShapeConnectorStartPoints();
          }
        };

        MouseOutHandler hideConnStartMouseOut = new MouseOutHandler() {

          public void onMouseOut(MouseOutEvent event) {
            endPointsShowTimer.schedule(END_POINTS_VIS_DELAY);
          }
        };

        showConnStartMouseHandler = this.addMouseOverHandler(showConnStartMouseOver);
        hideConnStartMouseHandler = this.addMouseOutHandler(hideConnStartMouseOut);
      }
    } else {
      if (endPointsShowTimer != null) {
        endPointsShowTimer.cancel();
      }
      if (showConnStartMouseHandler != null) {
        showConnStartMouseHandler.removeHandler();
      }
      if (hideConnStartMouseHandler != null) {
        hideConnStartMouseHandler.removeHandler();
      }
      for (EndPoint ep : endPoints) {
        if (ep instanceof ShapeConnectorStart) {
          ((ShapeConnectorStart) ep).removeHandlers();
        }
        ep.removeFromParent();
      }
      showConnStartMouseHandler = null;
      hideConnStartMouseHandler = null;
    }
  }

  public void refreshShapeCP() {
    if (endPointsShowTimer != null) {
      endPointsShowTimer.run();
    }
  }

  public boolean isConnectable() {
    return connectable;
  }

  public void makeConnectable(boolean enable) {
    connectable = enable;
    if (enable) {
      diagram.endPointDragController.registerDropController(shapeDropController);
      diagram.shapeDragController.registerDropController(shapeDropController);
    } else {
      try {
        diagram.endPointDragController.unregisterDropController(shapeDropController);
        diagram.shapeDragController.unregisterDropController(shapeDropController);
      } catch (Exception e) {
        LOG.log(Level.SEVERE, "error while disable connectors for shape", e);
      }
    }
  }

  public void changeConnectedWidget(Widget newConnectedWidget, CPShapeType newCpShapeType) {
    LOG.info("CHANGE connected widget (new shape type: " + newCpShapeType + ")");
    int oldWidgetWidth = connectedWidget.getOffsetWidth();
    int oldWidgetHeight = connectedWidget.getOffsetHeight();
    
    boolean startPointsWereVisible = startPointsVisible;
    if (startPointsVisible) {
      hideShapeConnectorStartPionts();
    }
    connectionPointsPanel.remove(connectedWidget);
    connectedWidget = newConnectedWidget;
    connectionPointsPanel.add(newConnectedWidget, CP_MARGIN + offsetLeft, CP_MARGIN + offsetTop);
    
    int newWidgetWidth = newConnectedWidget.getOffsetWidth();
    int newWidgetHeight = newConnectedWidget.getOffsetHeight();
    
    diagram.boundaryPanel.setWidgetPosition(this, 
        diagram.boundaryPanel.getWidgetLeft(this) - (newWidgetWidth - oldWidgetWidth) / 2, 
        diagram.boundaryPanel.getWidgetTop(this) - (newWidgetHeight - oldWidgetHeight) / 2);

    repaint(diagram, newCpShapeType);
    
    if (startPointsWereVisible) {
      showShapeConnectorStartPoints();
    }
  }
  
  private void reconnectEndPoints(List<EndPoint> endPointsToReconnect, List<ConnectionPoint> newConnectionPoints) {
    LOG.fine("RECONNECT end points (number of points to reconnect: " + endPointsToReconnect.size() + ")");
    for (EndPoint toReconnect : endPointsToReconnect) {
      toReconnect.unglueFromConnectionPoint();
      Point neighboringEP = toReconnect.findNeighboringEndPoint();
      ConnectionPoint newCp = findNearestConnectionPoint(neighboringEP.getLeft(), neighboringEP.getTop());
      toReconnect.glueToConnectionPoint(newCp);
      toReconnect.setPosition(newCp.getCenterLeft(), newCp.getCenterTop());
      
      //TODO change only two last sections, not recalculating whole connector
      toReconnect.connector.calculateStandardPointsPositions();
      toReconnect.connector.drawSections();
    }
  }
  
  public int getCenterLeft() {
    WidgetLocation location = new WidgetLocation(this, diagram.boundaryPanel);
    return location.getLeft() + getOffsetWidth() / 2;
  }
  
  public int getCenterTop() {
    WidgetLocation location = new WidgetLocation(this, diagram.boundaryPanel);
    return location.getTop() + getOffsetHeight() / 2;
  }
  
}
