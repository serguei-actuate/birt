/*******************************************************************************
 * Copyright (c) 2004 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.designer.internal.ui.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.birt.report.designer.core.model.schematic.ColumnHandleAdapter;
import org.eclipse.birt.report.designer.core.model.schematic.HandleAdapterFactory;
import org.eclipse.birt.report.designer.core.model.schematic.RowHandleAdapter;
import org.eclipse.birt.report.designer.core.model.schematic.TableHandleAdapter;
import org.eclipse.birt.report.designer.internal.ui.editors.schematic.editparts.GridEditPart;
import org.eclipse.birt.report.designer.internal.ui.editors.schematic.editparts.TableCellEditPart;
import org.eclipse.birt.report.designer.internal.ui.editors.schematic.editparts.TableEditPart;
import org.eclipse.birt.report.designer.util.FixTableLayoutCalculator;
import org.eclipse.birt.report.model.api.ColumnHandle;
import org.eclipse.birt.report.model.api.DimensionHandle;
import org.eclipse.birt.report.model.api.RowHandle;
import org.eclipse.birt.report.model.elements.DesignChoiceConstants;
import org.eclipse.core.runtime.Platform;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.LayoutManager;
import org.eclipse.draw2d.XYLayout;
import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.jface.viewers.StructuredSelection;

/**
 * The layout manager for Table report element
 *  
 */
/**
 * TableLayout
 */
public class TableLayout extends XYLayout
{

	/** The layout constraints */
	protected Map constraints = new HashMap( );
	private WorkingData data = null;
	private TableEditPart owner;

	private boolean needlayout = true;

	/**
	 * Default constructor
	 */
	public TableLayout( )
	{
		super( );
	}

	/**
	 * The constructor.
	 * 
	 * @param rowCount
	 * @param columnCount
	 */
	public TableLayout( TableEditPart part )
	{
		super( );
		this.owner = part;
	}

	/**
	 * Constructor
	 * 
	 * @param container
	 * @param bool
	 */
	public void layout( IFigure container, boolean bool )
	{
		boolean temp = needlayout;
		layout( container );
		if ( bool )
		{
			needlayout = temp;
		}
	}

	/**
	 * Mark dirty flag to trigger relayou.
	 */
	public void markDirty( )
	{
		needlayout = true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.draw2d.LayoutManager#layout(org.eclipse.draw2d.IFigure)
	 */
	public void layout( IFigure container )
	{
		if ( data != null
				&& data.columnWidths != null
				&& data.columnWidths.length == getColumnCount( )
				&& data.rowHeights != null
				&& data.rowHeights.length == getRowCount( )
				&& !needlayout
				|| !owner.isActive( ) )
		{
			return;
		}
		data = new WorkingData( );
		data.columnWidths = new TableLayoutData.ColumnData[getColumnCount( )];
		data.rowHeights = new TableLayoutData.RowData[getRowCount( )];

		// initialize the default value of each cell from DE model
		init( data.columnWidths, data.rowHeights );

		// get the figure list of all cell
		List children = container.getChildren( );

		//calculate the minimum width of each cell
		initMinSize( children );

		// be not implemented yet
		initMergeMinsize( children );

		// adjust the cell data with calculated width and height
		caleLayoutData( container );

		initRowMinSize( children );
		initRowMergeMinsize( children );
		caleRowData( );

		// set figure size with adjusted data
		layoutTable( container );

		setConstraint( container, data );
		needlayout = false;
		final List list = ( (StructuredSelection) getOwner( ).getViewer( )
				.getSelection( ) ).toList( );

		boolean hasCell = false;
		for ( int i = 0; i < list.size( ); i++ )
		{
			if ( list.get( i ) instanceof TableCellEditPart )
			{
				hasCell = true;
				break;
			}
		}

		if ( hasCell )
		{
			Platform.run( new SafeRunnable( ) {

				public void run( )
				{
					getOwner( ).getViewer( )
							.setSelection( new StructuredSelection( list ) );
				}
			} );
		}
	}

	private void layoutTable( IFigure container )
	{
		List children = container.getChildren( );
		int size = children.size( );
		Map map = getOwner( ).getViewer( ).getVisualPartMap( );

		for ( int i = 0; i < size; i++ )
		{
			IFigure figure = (IFigure) children.get( i );
			TableCellEditPart cellPart = (TableCellEditPart) map.get( figure );

			int rowNumber = cellPart.getRowNumber( );
			int columnNumber = cellPart.getColumnNumber( );
			int rowSpan = cellPart.getRowSpan( );
			int columnSpan = cellPart.getColSpan( );

			int x = getColumnWidth( 1, columnNumber );
			int y = getRowHeight( 1, rowNumber );
			int width = getColumnWidth( columnNumber, columnNumber + columnSpan );
			int height = getRowHeight( rowNumber, rowNumber + rowSpan );

			cellPart.markDirty( true, false );

			setBoundsOfChild( container, figure, new Rectangle( x,
					y,
					width,
					height ) );
		}
	}

	private int getRowHeight( int start, int end )
	{
		int retValue = 0;
		for ( int i = start; i < end; i++ )
		{
			retValue = retValue + data.rowHeights[i - 1].height;
		}
		return retValue;
	}

	private int getColumnWidth( int start, int end )
	{
		int retValue = 0;
		for ( int i = start; i < end; i++ )
		{
			retValue = retValue + data.columnWidths[i - 1].width;
		}
		return retValue;
	}

	protected void setBoundsOfChild( IFigure parent, IFigure child,
			Rectangle bounds )
	{
		parent.getClientArea( Rectangle.SINGLETON );
		bounds.translate( Rectangle.SINGLETON.x, Rectangle.SINGLETON.y );

		// comment out to force invalidation.
		//if ( !bounds.equals( child.getBounds( ) ) )
		{
			child.setBounds( bounds );
			if ( child.getLayoutManager( ) != null )
				child.getLayoutManager( ).invalidate( );
			child.revalidate( );
		}
	}

	private void initRowMinSize( List children )
	{
		int size = children.size( );
		Map map = getOwner( ).getViewer( ).getVisualPartMap( );

		for ( int i = 0; i < size; i++ )
		{
			IFigure figure = (IFigure) children.get( i );
			TableCellEditPart cellPart = (TableCellEditPart) map.get( figure );

			int rowNumber = cellPart.getRowNumber( );
			int columnNumber = cellPart.getColumnNumber( );
			int columnSpan = cellPart.getColSpan( );
			int rowSpan = cellPart.getRowSpan( );

			TableLayoutData.RowData rowData = data.findRowData( rowNumber );
			TableLayoutData.ColumnData columnData = data.findColumnData( columnNumber );

			int colWidth = columnData.width;

			if ( columnSpan > 1 )
			{
				for ( int k = 1; k < columnSpan; k++ )
				{
					TableLayoutData.ColumnData cData = data.findColumnData( columnNumber
							+ k );

					if ( cData != null )
					{
						colWidth += cData.width;
					}
				}
			}

			Dimension dim = figure.getMinimumSize( colWidth, -1 );

			if ( dim.height > rowData.minRowHeight && rowSpan == 1 )
			{
				rowData.minRowHeight = dim.height;
			}

			if ( dim.height > rowData.trueMinRowHeight && rowSpan == 1 )
			{
				rowData.trueMinRowHeight = dim.height;
				rowData.isSetting = true;
			}
		}
	}

	private void initRowMergeMinsize( List children )
	{
		int size = children.size( );
		Map map = getOwner( ).getViewer( ).getVisualPartMap( );
		List list = new ArrayList( );
		List adjustRow = new ArrayList( );

		for ( int i = 0; i < size; i++ )
		{
			IFigure figure = (IFigure) children.get( i );
			TableCellEditPart cellPart = (TableCellEditPart) map.get( figure );

			int rowNumber = cellPart.getRowNumber( );

			int rowSpan = cellPart.getRowSpan( );

			if ( rowSpan == 1 )
			{
				continue;
			}

			list.add( figure );

			if ( rowSpan > 1 )
			{
				for ( int j = rowNumber; j < rowNumber + rowSpan; j++ )
				{
					adjustRow.add( new Integer( j ) );
				}
			}
		}

		caleRowMergeMinHeight( list, adjustRow, new ArrayList( ) );

	}

	private void caleRowMergeMinHeight( List figures, List adjust,
			List hasAdjust )
	{
		if ( adjust.isEmpty( ) )
		{
			return;
		}
		int size = figures.size( );
		Map map = getOwner( ).getViewer( ).getVisualPartMap( );
		int adjustMax = 0;
		int trueAdjustMax = 0;
		int adjustMaxNumber = 0;

		for ( int i = 0; i < size; i++ )
		{
			IFigure figure = (IFigure) figures.get( i );
			TableCellEditPart cellPart = (TableCellEditPart) map.get( figure );
			int rowNumber = cellPart.getRowNumber( );
			int rowSpan = cellPart.getRowSpan( );

			Dimension minSize = figure.getMinimumSize( data.findColumnData( cellPart.getColumnNumber( ) ).width,
					-1 );

			int samMin = 0;
			int trueSamMin = 0;

			int[] adjustNumber = new int[0];
			for ( int j = rowNumber; j < rowNumber + rowSpan; j++ )
			{
				TableLayoutData.RowData rowData = data.findRowData( j );
				if ( !hasAdjust.contains( new Integer( j ) ) )
				{
					int len = adjustNumber.length;
					int temp[] = new int[len + 1];
					System.arraycopy( adjustNumber, 0, temp, 0, len );
					temp[len] = j;
					adjustNumber = temp;
				}
				else
				{
					samMin = samMin + rowData.trueMinRowHeight;
					trueSamMin = trueSamMin + rowData.trueMinRowHeight;
				}
			}
			int adjustCount = adjustNumber.length;
			if ( adjustCount == 0 )
			{
				continue;
			}
			int value = minSize.height - samMin;
			int trueValue = minSize.height - trueSamMin;
			for ( int j = 0; j < adjustCount; j++ )
			{
				int temp = 0;
				int trueTemp = 0;
				if ( j == adjustCount - 1 )
				{
					temp = value / adjustCount + value % adjustCount;
					trueTemp = trueValue
							/ adjustCount
							+ trueValue
							% adjustCount;
				}
				else
				{
					temp = value / adjustCount;
					trueTemp = trueValue / adjustCount;
				}
				TableLayoutData.RowData rowData = data.findRowData( adjustNumber[j] );
				temp = Math.max( temp, rowData.minRowHeight );
				trueTemp = Math.max( trueTemp, rowData.trueMinRowHeight );

				if ( trueTemp > trueAdjustMax )
				{
					adjustMax = temp;
					trueAdjustMax = trueTemp;
					adjustMaxNumber = adjustNumber[j];
				}
			}
		}

		if ( adjustMaxNumber > 0 )
		{
			TableLayoutData.RowData rowData = data.findRowData( adjustMaxNumber );
			rowData.minRowHeight = adjustMax;
			rowData.trueMinRowHeight = trueAdjustMax;
			adjust.remove( new Integer( adjustMaxNumber ) );
			hasAdjust.add( new Integer( adjustMaxNumber ) );
			caleMergeMinHeight( figures, adjust, hasAdjust );
		}
	}

	private void caleRowData( )
	{

		if ( data == null )
		{
			return;
		}

		int size = data.rowHeights.length;
		int dxRows[] = new int[size];
		int dxTotal = 0;
		for ( int i = 0; i < size; i++ )
		{
			dxRows[i] = data.rowHeights[i].height
					- data.rowHeights[i].trueMinRowHeight;
			dxTotal = dxTotal + dxRows[i];
		}

		for ( int i = 0; i < size; i++ )
		{
			if ( dxRows[i] < 0 )
			{
				data.rowHeights[i].height = data.rowHeights[i].trueMinRowHeight;
			}
		}

	}

	private void caleLayoutData( IFigure container )
	{
		if ( data == null )
		{
			return;
		}

		/**
		 * layout row, the row/column layout sequence can be changed.
		 */
		int size = data.rowHeights.length;
		int dxRows[] = new int[size];
		int dxTotal = 0;

		for ( int i = 0; i < size; i++ )
		{
			dxRows[i] = data.rowHeights[i].height
					- data.rowHeights[i].trueMinRowHeight;
			dxTotal = dxTotal + dxRows[i];
		}

		for ( int i = 0; i < size; i++ )
		{
			if ( dxRows[i] < 0 )
			{
				data.rowHeights[i].height = data.rowHeights[i].trueMinRowHeight;
			}
		}

		/**
		 * layout column
		 */
		size = data.columnWidths.length;

		int containerWidth = getOwner( ).getFigure( )
				.getParent( )
				.getClientArea( )
				.getSize( ).width;

		TableHandleAdapter tadp = null;

		if ( getOwner( ) instanceof GridEditPart )
		{
			tadp = HandleAdapterFactory.getInstance( )
					.getGridHandleAdapter( getOwner( ).getModel( ) );
		}
		else
		{
			tadp = HandleAdapterFactory.getInstance( )
					.getTableHandleAdapter( getOwner( ).getModel( ) );
		}

		if ( tadp != null )
		{
			String ww = tadp.getDefinedWidth( );

			containerWidth = getDefinedWidth( ww, containerWidth );
		}

		int padding = getOwner( ).getFigure( )
				.getBorder( )
				.getInsets( getOwner( ).getFigure( ) )
				.getWidth( );

		containerWidth -= padding;

		containerWidth = Math.max( 0, containerWidth );

		String[] definedWidth = new String[size];
		for ( int i = 1; i < size + 1; i++ )
		{
			Object obj = getOwner( ).getColumn( i );
			ColumnHandleAdapter adapt = HandleAdapterFactory.getInstance( )
					.getColumnHandleAdapter( obj );
			definedWidth[i - 1] = adapt.getRawWidth( );
		}

		FixTableLayoutCalculator calculator = new FixTableLayoutCalculator( );
		calculator.setTableWidth( containerWidth );
		calculator.setColMinSize( ColumnHandleAdapter.DEFAULT_MINWIDTH );
		calculator.setDefinedColWidth( definedWidth );

		TableLayoutHelper.calculateColumnWidth( data.columnWidths,
				containerWidth,
				calculator );
	}

	private int getDefinedWidth( String dw, int cw )
	{
		if ( dw == null || dw.length( ) == 0 )
		{
			return 0;
		}

		try
		{
			if ( dw.endsWith( "%" ) ) //$NON-NLS-1$
			{
				return (int) ( Double.parseDouble( dw.substring( 0,
						dw.length( ) - 1 ) )
						* cw / 100 );
			}

			return (int) Double.parseDouble( dw );
		}
		catch ( NumberFormatException e )
		{
			//ignore.
		}

		return 0;
	}

	/**
	 * @param children
	 */
	private void initMinSize( List children )
	{
		int size = children.size( );
		Map map = getOwner( ).getViewer( ).getVisualPartMap( );

		for ( int i = 0; i < size; i++ )
		{
			IFigure figure = (IFigure) children.get( i );
			TableCellEditPart cellPart = (TableCellEditPart) map.get( figure );

			int rowNumber = cellPart.getRowNumber( );
			int columnNumber = cellPart.getColumnNumber( );
			int rowSpan = cellPart.getRowSpan( );
			int columnSpan = cellPart.getColSpan( );

			//may be implement a interface
			// get minimum size of cell figure
			Dimension dim = figure.getMinimumSize( );

			TableLayoutData.RowData rowData = data.findRowData( rowNumber );
			TableLayoutData.ColumnData columnData = data.findColumnData( columnNumber );

			if ( dim.height > rowData.minRowHeight && rowSpan == 1 )
			{
				rowData.minRowHeight = dim.height;
			}

			if ( dim.height > rowData.trueMinRowHeight && rowSpan == 1 )
			{
				rowData.trueMinRowHeight = dim.height;
				rowData.isSetting = true;
			}

			// max(defaultValue,MCW)
			if ( dim.width > columnData.minColumnWidth && columnSpan == 1 )
			{
				columnData.minColumnWidth = dim.width;
			}

			if ( dim.width > columnData.trueMinColumnWidth && columnSpan == 1 )
			{
				columnData.trueMinColumnWidth = dim.width;
				columnData.isSetting = true;
			}

		}
	}

	/**
	 * @param children
	 */
	private void initMergeMinsize( List children )
	{
		int size = children.size( );
		Map map = getOwner( ).getViewer( ).getVisualPartMap( );
		List list = new ArrayList( );
		List adjustRow = new ArrayList( );
		List adjustColumn = new ArrayList( );

		for ( int i = 0; i < size; i++ )
		{
			IFigure figure = (IFigure) children.get( i );
			TableCellEditPart cellPart = (TableCellEditPart) map.get( figure );

			int rowNumber = cellPart.getRowNumber( );
			int columnNumber = cellPart.getColumnNumber( );

			int rowSpan = cellPart.getRowSpan( );
			int columnSpan = cellPart.getColSpan( );

			if ( rowSpan == 1 && columnSpan == 1 )
			{
				continue;
			}

			list.add( figure );

			if ( rowSpan > 1 )
			{
				for ( int j = rowNumber; j < rowNumber + rowSpan; j++ )
				{
					adjustRow.add( new Integer( j ) );
				}
			}

			if ( columnSpan > 1 )
			{
				for ( int j = columnNumber; j < columnNumber + columnSpan; j++ )
				{
					adjustColumn.add( new Integer( j ) );
				}
			}
		}

		caleMergeMinHeight( list, adjustRow, new ArrayList( ) );
		caleMergeMinWidth( list, adjustColumn, new ArrayList( ) );
	}

	private void caleMergeMinHeight( List figures, List adjust, List hasAdjust )
	{
		if ( adjust.isEmpty( ) )
		{
			return;
		}

		int size = figures.size( );
		Map map = getOwner( ).getViewer( ).getVisualPartMap( );
		int adjustMax = 0;
		int trueAdjustMax = 0;
		int adjustMaxNumber = 0;

		for ( int i = 0; i < size; i++ )
		{
			IFigure figure = (IFigure) figures.get( i );
			TableCellEditPart cellPart = (TableCellEditPart) map.get( figure );
			int rowNumber = cellPart.getRowNumber( );
			int rowSpan = cellPart.getRowSpan( );

			Dimension minSize = figure.getMinimumSize( );
			int samMin = 0;
			int trueSamMin = 0;

			int[] adjustNumber = new int[0];
			for ( int j = rowNumber; j < rowNumber + rowSpan; j++ )
			{
				TableLayoutData.RowData rowData = data.findRowData( j );
				if ( !hasAdjust.contains( new Integer( j ) ) )
				{
					int len = adjustNumber.length;
					int temp[] = new int[len + 1];
					System.arraycopy( adjustNumber, 0, temp, 0, len );
					temp[len] = j;
					adjustNumber = temp;
				}
				else
				{
					samMin = samMin + rowData.trueMinRowHeight;
					trueSamMin = trueSamMin + rowData.trueMinRowHeight;
				}
			}

			int adjustCount = adjustNumber.length;
			if ( adjustCount == 0 )
			{
				continue;
			}

			int value = minSize.height - samMin;
			int trueValue = minSize.height - trueSamMin;
			//int trueAvage = minSize.h/adjustCount;
			for ( int j = 0; j < adjustCount; j++ )
			{
				int temp = 0;
				int trueTemp = 0;
				if ( j == adjustCount - 1 )
				{
					temp = value / adjustCount + value % adjustCount;
					trueTemp = trueValue
							/ adjustCount
							+ trueValue
							% adjustCount;
				}
				else
				{
					temp = value / adjustCount;
					trueTemp = trueValue / adjustCount;
				}
				TableLayoutData.RowData rowData = data.findRowData( adjustNumber[j] );
				temp = Math.max( temp, rowData.minRowHeight );
				trueTemp = Math.max( trueTemp, rowData.trueMinRowHeight );

				if ( trueTemp > trueAdjustMax )
				{
					adjustMax = temp;
					trueAdjustMax = trueTemp;
					adjustMaxNumber = adjustNumber[j];
				}
			}
		}

		if ( adjustMaxNumber > 0 )
		{
			TableLayoutData.RowData rowData = data.findRowData( adjustMaxNumber );
			rowData.minRowHeight = adjustMax;
			rowData.trueMinRowHeight = trueAdjustMax;
			adjust.remove( new Integer( adjustMaxNumber ) );
			hasAdjust.add( new Integer( adjustMaxNumber ) );
			caleMergeMinHeight( figures, adjust, hasAdjust );
		}
	}

	private void caleMergeMinWidth( List figures, List adjust, List hasAdjust )
	{
		if ( adjust.isEmpty( ) )
		{
			return;
		}

		int size = figures.size( );
		Map map = getOwner( ).getViewer( ).getVisualPartMap( );
		int adjustMax = 0;
		int trueAdjustMax = 0;
		int adjustMaxNumber = 0;

		for ( int i = 0; i < size; i++ )
		{
			IFigure figure = (IFigure) figures.get( i );
			TableCellEditPart cellPart = (TableCellEditPart) map.get( figure );
			int columnNumber = cellPart.getColumnNumber( );
			int columnSpan = cellPart.getColSpan( );

			Dimension minSize = figure.getMinimumSize( );
			int samMin = 0;
			int trueSamMin = 0;

			int[] adjustNumber = new int[0];
			for ( int j = columnNumber; j < columnNumber + columnSpan; j++ )
			{
				TableLayoutData.ColumnData columnData = data.findColumnData( j );

				if ( !hasAdjust.contains( new Integer( j ) ) )
				{
					int len = adjustNumber.length;
					int temp[] = new int[len + 1];
					System.arraycopy( adjustNumber, 0, temp, 0, len );
					temp[len] = j;
					adjustNumber = temp;
				}
				else
				{
					samMin = samMin + columnData.trueMinColumnWidth;
					trueSamMin = trueSamMin + columnData.trueMinColumnWidth;
				}
			}

			int adjustCount = adjustNumber.length;
			if ( adjustCount == 0 )
			{
				continue;
			}

			int value = minSize.width - samMin;
			int trueValue = minSize.width - trueSamMin;

			for ( int j = 0; j < adjustCount; j++ )
			{
				int temp = 0;
				int trueTemp = 0;
				if ( j == adjustCount - 1 )
				{
					temp = value / adjustCount + value % adjustCount;
					trueTemp = trueValue
							/ adjustCount
							+ trueValue
							% adjustCount;
				}
				else
				{
					temp = value / adjustCount;
					trueTemp = trueValue / adjustCount;
				}

				TableLayoutData.ColumnData columnData = data.findColumnData( adjustNumber[j] );
				temp = Math.max( temp, columnData.minColumnWidth );
				trueTemp = Math.max( trueTemp, columnData.trueMinColumnWidth );

				if ( trueTemp > trueAdjustMax )
				{
					adjustMax = temp;
					trueAdjustMax = trueTemp;
					adjustMaxNumber = adjustNumber[j];
				}
			}
		}

		if ( adjustMaxNumber > 0 )
		{
			TableLayoutData.ColumnData columnData = data.findColumnData( adjustMaxNumber );
			columnData.minColumnWidth = adjustMax;
			columnData.trueMinColumnWidth = trueAdjustMax;
			adjust.remove( new Integer( adjustMaxNumber ) );
			hasAdjust.add( new Integer( adjustMaxNumber ) );
			caleMergeMinWidth( figures, adjust, hasAdjust );
		}

	}

	private void init( TableLayoutData.ColumnData[] columnWidths,
			TableLayoutData.RowData[] rowHeights )
	{
		int size = rowHeights.length;
		for ( int i = 1; i < size + 1; i++ )
		{
			rowHeights[i - 1] = new TableLayoutData.RowData( );
			rowHeights[i - 1].rowNumber = i;
			Object obj = getOwner( ).getRow( i );
			RowHandleAdapter adapt = HandleAdapterFactory.getInstance( )
					.getRowHandleAdapter( obj );
			rowHeights[i - 1].height = adapt.getHeight( );
			rowHeights[i - 1].isForce = adapt.isCustomHeight( );

			//add to handle percentage case.
			DimensionHandle dim = ( (RowHandle) adapt.getHandle( ) ).getHeight( );
			if ( DesignChoiceConstants.UNITS_PERCENTAGE.equals( dim.getUnits( ) )
					&& dim.getMeasure( ) > 0 )
			{
				rowHeights[i - 1].isPercentage = true;
				rowHeights[i - 1].percentageHeight = dim.getMeasure( );
			}

			//add to handle auto case;
			if ( dim.getUnits( ) == null || dim.getUnits( ).length( ) == 0 )
			{
				rowHeights[i - 1].isAuto = true;
			}

			//add by gao 2004.11.22
			rowHeights[i - 1].trueMinRowHeight = ( rowHeights[i - 1].isForce && !rowHeights[i - 1].isPercentage ) ? rowHeights[i - 1].height
					: rowHeights[i - 1].minRowHeight;

		}

		size = columnWidths.length;
		for ( int i = 1; i < size + 1; i++ )
		{
			columnWidths[i - 1] = new TableLayoutData.ColumnData( );
			columnWidths[i - 1].columnNumber = i;
			Object obj = getOwner( ).getColumn( i );
			ColumnHandleAdapter adapt = HandleAdapterFactory.getInstance( )
					.getColumnHandleAdapter( obj );
			columnWidths[i - 1].width = adapt.getWidth( );
			columnWidths[i - 1].isForce = adapt.isCustomWidth( );

			//add to handle percentage case.
			DimensionHandle dim = ( (ColumnHandle) adapt.getHandle( ) ).getWidth( );
			if ( DesignChoiceConstants.UNITS_PERCENTAGE.equals( dim.getUnits( ) )
					&& dim.getMeasure( ) > 0 )
			{
				columnWidths[i - 1].isPercentage = true;
				columnWidths[i - 1].percentageWidth = dim.getMeasure( );
			}

			//add to handle auto case;
			if ( dim.getUnits( ) == null || dim.getUnits( ).length( ) == 0 )
			{
				columnWidths[i - 1].isAuto = true;
			}

			//added by gao 2004.11.22
			columnWidths[i - 1].trueMinColumnWidth = ( columnWidths[i - 1].isForce && !columnWidths[i - 1].isPercentage ) ? columnWidths[i - 1].width
					: columnWidths[i - 1].minColumnWidth;
		}

	}

	/**
	 * @see LayoutManager#getConstraint(IFigure)
	 */
	public Object getConstraint( IFigure figure )
	{
		return constraints.get( figure );
	}

	/**
	 * @see LayoutManager#remove(IFigure)
	 */
	public void remove( IFigure figure )
	{
		super.remove( figure );
		constraints.remove( figure );
	}

	/**
	 * Sets the layout constraint of the given figure. The constraints can only
	 * be of type {@link Rectangle}.
	 * 
	 * @see LayoutManager#setConstraint(IFigure, Object)
	 */
	public void setConstraint( IFigure figure, Object newConstraint )
	{
		super.setConstraint( figure, newConstraint );
		if ( newConstraint != null )
			constraints.put( figure, newConstraint );
	}

	/**
	 * @return column count
	 */
	public int getColumnCount( )
	{
		return getOwner( ).getColumnCount( );
	}

	/**
	 * Gets row count of Row
	 * 
	 * @return
	 */
	public int getRowCount( )
	{
		return getOwner( ).getRowCount( );
	}

	/**
	 * Keeps table layout information includes columns width, rows height
	 *  
	 */
	public static class WorkingData
	{

		public TableLayoutData.ColumnData columnWidths[];
		public TableLayoutData.RowData rowHeights[];

		public TableLayoutData.RowData findRowData( int number )
		{
			return rowHeights[number - 1];
		}

		public TableLayoutData.ColumnData findColumnData( int number )
		{
			return columnWidths[number - 1];
		}
	}

	/**
	 * Gets the table edit part of, which owned this layout manager
	 * 
	 * @return
	 */
	public TableEditPart getOwner( )
	{
		return owner;
	}

	protected Dimension calculateMinimumSize( IFigure figure, int wHint,
			int hHint )
	{
		layout( figure, true );

		IFigure table = figure.getParent( ).getParent( ).getParent( );
		int widthExpand = table.getInsets( ).getWidth( );

		int width = 0;
		int size = data.columnWidths.length;
		for ( int i = 0; i < size; i++ )
		{
			width = width + data.columnWidths[i].trueMinColumnWidth;
		}

		TableHandleAdapter tadp = null;

		if ( getOwner( ) instanceof GridEditPart )
		{
			tadp = HandleAdapterFactory.getInstance( )
					.getGridHandleAdapter( getOwner( ).getModel( ) );
		}
		else
		{
			tadp = HandleAdapterFactory.getInstance( )
					.getTableHandleAdapter( getOwner( ).getModel( ) );
		}

		if ( tadp != null )
		{
			String ww = tadp.getDefinedWidth( );

			if ( ww != null
					&& ww.length( ) > 0
					&& !ww.endsWith( DesignChoiceConstants.UNITS_PERCENTAGE ) )
			{
				try
				{
					int dwidth = Integer.parseInt( ww );

					if ( dwidth > width + widthExpand )
					{
						width = dwidth - widthExpand;
					}
				}
				catch ( Exception e )
				{
					//ignore;
				}
			}
		}

		int height = 0;
		size = data.rowHeights.length;
		for ( int i = 0; i < size; i++ )
		{
			height = height + data.rowHeights[i].height;
		}
		Dimension dim = new Dimension( width, height );

		return dim.expand( table.getInsets( ).getWidth( ), table.getInsets( )
				.getHeight( ) );
	}

	/**
	 * @see org.eclipse.draw2d.LayoutManager#getMinimumSize(org.eclipse.draw2d.IFigure,
	 *      int, int)
	 */
	public Dimension getMinimumSize( IFigure container, int wHint, int hHint )
	{
		return calculateMinimumSize( container, wHint, hHint );
	}
}