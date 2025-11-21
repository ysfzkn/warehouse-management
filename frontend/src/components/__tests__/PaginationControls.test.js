import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import PaginationControls from '../PaginationControls';

describe('PaginationControls', () => {
  it('returns null when only one page exists', () => {
    const { container } = render(<PaginationControls page={0} totalPages={1} onPageChange={() => {}} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders buttons and handles navigation clicks', () => {
    const handleChange = jest.fn();
    render(<PaginationControls page={0} totalPages={3} onPageChange={handleChange} />);

    const prevButton = screen.getByLabelText('Önceki sayfa');
    expect(prevButton.closest('li')).toHaveClass('disabled');

    const pageTwoButton = screen.getByRole('button', { name: '2' });
    fireEvent.click(pageTwoButton);
    expect(handleChange).toHaveBeenCalledWith(1);

    const nextButton = screen.getByLabelText('Sonraki sayfa');
    fireEvent.click(nextButton);
    expect(handleChange).toHaveBeenCalledWith(1); // remain 1? hmm our change function prevents duplicates. But after page change we still same page? realize component uses prop page=0 so after clicking page 2, we expect onChange called with 1 again? but we already call once; next click increments to 1? but since prop page still 0, hitting next triggers onChange(1). that'll mean handleChange called twice both with 1. test to ensure second call? maybe treat differently? we can expect second call to have been calledTimes(2). We'll adjust to check call count.

    expect(handleChange).toHaveBeenCalledTimes(2);
  });
});

